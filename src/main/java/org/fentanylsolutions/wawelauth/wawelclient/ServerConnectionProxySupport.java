package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import net.minecraft.network.NetworkManager;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxyType;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderProxySupport;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

@SideOnly(Side.CLIENT)
public final class ServerConnectionProxySupport {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final OioEventLoopGroup PROXY_EVENT_LOOPS = new OioEventLoopGroup(
        0,
        new ThreadFactoryBuilder().setNameFormat("Netty Proxy Client IO #%d")
            .setDaemon(true)
            .build());

    private ServerConnectionProxySupport() {}

    public static ProviderProxySettings normalizeSettings(ProviderProxySettings settings) {
        ProviderProxySettings normalized = new ProviderProxySettings();
        if (settings == null) {
            normalized.setType(ProviderProxyType.SOCKS);
            normalized.setEnabled(false);
            return normalized;
        }

        normalized.setEnabled(settings.isEnabled());
        normalized.setType(settings.getType() != null ? settings.getType() : ProviderProxyType.SOCKS);
        normalized.setHost(trimToNull(settings.getHost()));
        normalized.setPort(settings.getPort());
        normalized.setUsername(trimToNull(settings.getUsername()));
        normalized.setPassword(trimToNull(settings.getPassword()));
        return normalized;
    }

    public static ProviderProxySettings copySettings(ProviderProxySettings settings) {
        return normalizeSettings(settings);
    }

    public static void validateSettings(ProviderProxySettings settings) {
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (!settings.hasEndpoint()) {
            throw new IllegalArgumentException("Proxy address and port are required.");
        }
        Integer port = settings.getPort();
        if (port == null || port.intValue() < 1 || port.intValue() > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535.");
        }
        if (settings.getPassword() != null && settings.getUsername() == null) {
            throw new IllegalArgumentException("Proxy username is required when a proxy password is set.");
        }
    }

    public static boolean shouldUseGameplayProxy(ProviderProxySettings settings) {
        return settings != null && settings.isEnabled() && settings.hasEndpoint();
    }

    public static void probeGameServerConnection(String host, int port, ProviderProxySettings settings)
        throws IOException {
        ProviderProxySettings normalized = normalizeSettings(settings);
        validateSettings(normalized);
        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(normalized);
            Socket socket = createGameplaySocket(normalized)) {
            try {
                connectSocketThroughGameplayProxy(
                    socket,
                    InetSocketAddress.createUnresolved(host, port),
                    null,
                    normalized,
                    CONNECT_TIMEOUT_MS);
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Failed to connect to game server via proxy.", e);
            }
        }
    }

    public static NetworkManager createProxiedLanClient(String host, int port, ProviderProxySettings settings) {
        final ProviderProxySettings normalized = normalizeSettings(settings);
        validateSettings(normalized);
        WawelAuth.debug(
            "Connecting to game server " + host
                + ":"
                + port
                + " via gameplay proxy "
                + ProviderProxySupport.describeProxySettings(normalized));

        final NetworkManager networkmanager = new NetworkManager(true);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(PROXY_EVENT_LOOPS)
            .channelFactory(new ChannelFactory<Channel>() {

                @Override
                public Channel newChannel() {
                    try {
                        return new GameplayProxySocketChannel(normalized);
                    } catch (IOException e) {
                        throw new ChannelException("Failed to create proxied socket", e);
                    }
                }
            })
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.valueOf(CONNECT_TIMEOUT_MS))
            .handler(new ChannelInitializer() {

                @Override
                protected void initChannel(Channel channel) {
                    try {
                        channel.config()
                            .setOption(ChannelOption.IP_TOS, Integer.valueOf(24));
                    } catch (ChannelException ignored) {}

                    try {
                        channel.config()
                            .setOption(ChannelOption.TCP_NODELAY, Boolean.valueOf(false));
                    } catch (ChannelException ignored) {}

                    channel.pipeline()
                        .addLast("timeout", new ReadTimeoutHandler(20))
                        .addLast("splitter", new MessageDeserializer2())
                        .addLast("decoder", new MessageDeserializer(NetworkManager.field_152462_h))
                        .addLast("prepender", new MessageSerializer2())
                        .addLast("encoder", new MessageSerializer(NetworkManager.field_152462_h))
                        .addLast("packet_handler", networkmanager);
                }
            });

        try (ProviderProxySupport.AuthContext ignored = ProviderProxySupport.enterAuthContext(normalized)) {
            bootstrap.connect(InetSocketAddress.createUnresolved(host, port))
                .syncUninterruptibly();
        }

        return networkmanager;
    }

    private static Socket createGameplaySocket(ProviderProxySettings settings) {
        if (settings != null && settings.getType() == ProviderProxyType.SOCKS) {
            Proxy proxy = new Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(
                    settings.getHost()
                        .trim(),
                    settings.getPort()
                        .intValue()));
            return new Socket(proxy);
        }
        return new Socket();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void connectSocketThroughGameplayProxy(Socket socket, SocketAddress remoteAddress,
        SocketAddress localAddress, ProviderProxySettings settings, int connectTimeoutMs) throws Exception {
        if (!(remoteAddress instanceof InetSocketAddress)) {
            throw new ConnectException("Unsupported remote address: " + remoteAddress);
        }
        InetSocketAddress remote = (InetSocketAddress) remoteAddress;

        if (localAddress != null) {
            socket.bind(localAddress);
        }

        if (settings == null || settings.getType() == ProviderProxyType.SOCKS) {
            socket.connect(remote, connectTimeoutMs);
            return;
        }

        connectViaHttpProxy(socket, remote, settings, connectTimeoutMs);
    }

    private static void connectViaHttpProxy(Socket socket, InetSocketAddress remote, ProviderProxySettings settings,
        int connectTimeoutMs) throws Exception {
        InetSocketAddress proxyAddress = new InetSocketAddress(
            settings.getHost()
                .trim(),
            settings.getPort()
                .intValue());

        try {
            socket.connect(proxyAddress, connectTimeoutMs);
        } catch (SocketTimeoutException e) {
            ConnectTimeoutException cause = new ConnectTimeoutException("connection timed out: " + proxyAddress);
            cause.setStackTrace(e.getStackTrace());
            throw cause;
        }

        int previousTimeout = socket.getSoTimeout();
        socket.setSoTimeout(connectTimeoutMs);
        try {
            performHttpConnectHandshake(socket, remote, settings);
        } finally {
            socket.setSoTimeout(previousTimeout);
        }
    }

    private static void performHttpConnectHandshake(Socket socket, InetSocketAddress remote,
        ProviderProxySettings settings) throws IOException {
        String authority = formatAuthority(extractHost(remote), remote.getPort());
        OutputStream output = socket.getOutputStream();
        StringBuilder request = new StringBuilder();
        request.append("CONNECT ")
            .append(authority)
            .append(" HTTP/1.1\r\n")
            .append("Host: ")
            .append(authority)
            .append("\r\n")
            .append("Proxy-Connection: Keep-Alive\r\n");
        if (settings.hasCredentials()) {
            request.append("Proxy-Authorization: Basic ")
                .append(encodeBasicCredentials(settings))
                .append("\r\n");
        }
        request.append("\r\n");
        output.write(
            request.toString()
                .getBytes(StandardCharsets.ISO_8859_1));
        output.flush();

        InputStream input = socket.getInputStream();
        String statusLine = readHttpLine(input);
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IOException("HTTP proxy did not return a response to CONNECT.");
        }

        while (true) {
            String headerLine = readHttpLine(input);
            if (headerLine == null || headerLine.isEmpty()) {
                break;
            }
        }

        int statusCode = parseHttpStatusCode(statusLine);
        if (statusCode != 200) {
            throw new IOException("Unable to tunnel through proxy. Proxy returns \"" + statusLine + "\"");
        }
    }

    private static String encodeBasicCredentials(ProviderProxySettings settings) {
        String username = settings.getUsername() != null ? settings.getUsername() : "";
        String password = settings.getPassword() != null ? settings.getPassword() : "";
        return Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractHost(InetSocketAddress address) {
        String host = address.getHostString();
        if (host != null && !host.isEmpty()) {
            return host;
        }
        if (address.getAddress() != null) {
            return address.getAddress()
                .getHostAddress();
        }
        return address.getHostName();
    }

    private static String formatAuthority(String host, int port) {
        String normalizedHost = host;
        if (normalizedHost.indexOf(':') >= 0 && !normalizedHost.startsWith("[") && !normalizedHost.endsWith("]")) {
            normalizedHost = "[" + normalizedHost + "]";
        }
        return normalizedHost + ":" + port;
    }

    private static String readHttpLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int next = input.read();
            if (next < 0) {
                return line.length() == 0 ? null : line.toString();
            }
            if (next == '\n') {
                return line.toString();
            }
            if (next != '\r') {
                line.append((char) next);
            }
        }
    }

    private static int parseHttpStatusCode(String statusLine) throws IOException {
        int firstSpace = statusLine.indexOf(' ');
        if (firstSpace < 0) {
            throw new IOException("Invalid HTTP proxy response: " + statusLine);
        }
        int secondSpace = statusLine.indexOf(' ', firstSpace + 1);
        String code = secondSpace > firstSpace ? statusLine.substring(firstSpace + 1, secondSpace)
            : statusLine.substring(firstSpace + 1);
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid HTTP proxy response: " + statusLine, e);
        }
    }

    private static final class GameplayProxySocketChannel extends OioSocketChannel {

        private final Socket proxySocket;
        private final ProviderProxySettings settings;

        private GameplayProxySocketChannel(ProviderProxySettings settings) throws IOException {
            this(createGameplaySocket(settings), settings);
        }

        private GameplayProxySocketChannel(Socket socket, ProviderProxySettings settings) {
            super(socket);
            this.proxySocket = socket;
            this.settings = settings;
        }

        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
            boolean success = false;
            try {
                connectSocketThroughGameplayProxy(
                    proxySocket,
                    remoteAddress,
                    localAddress,
                    settings,
                    config().getConnectTimeoutMillis());
                activate(proxySocket.getInputStream(), proxySocket.getOutputStream());
                success = true;
            } finally {
                if (!success) {
                    doClose();
                }
            }
        }
    }
}
