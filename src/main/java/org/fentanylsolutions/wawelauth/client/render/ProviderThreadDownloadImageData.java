package org.fentanylsolutions.wawelauth.client.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.data.ProviderProxySettings;
import org.fentanylsolutions.wawelauth.wawelclient.http.ProviderRoutedHttp;

/**
 * A ThreadDownloadImageData variant that can opt into an exact provider proxy
 * route when the texture URL was freshly seen in one provider response.
 */
public class ProviderThreadDownloadImageData extends SimpleTexture {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final File cacheFile;
    private final String imageUrl;
    private final IImageBuffer imageBuffer;
    private final ProviderProxySettings proxySettings;
    private final String providerName;
    private BufferedImage bufferedImage;
    private Thread imageThread;
    private boolean textureUploaded;

    public ProviderThreadDownloadImageData(File cacheFile, String imageUrl, ResourceLocation textureLocation,
        IImageBuffer imageBuffer) {
        this(cacheFile, imageUrl, textureLocation, imageBuffer, (ClientProvider) null);
    }

    public ProviderThreadDownloadImageData(File cacheFile, String imageUrl, ResourceLocation textureLocation,
        IImageBuffer imageBuffer, ClientProvider provider) {
        this(cacheFile, imageUrl, textureLocation, imageBuffer, settingsFor(provider), providerName(provider));
    }

    public ProviderThreadDownloadImageData(File cacheFile, String imageUrl, ResourceLocation textureLocation,
        IImageBuffer imageBuffer, ProviderProxySettings proxySettings, String providerName) {
        super(textureLocation);
        this.cacheFile = cacheFile;
        this.imageUrl = imageUrl;
        this.imageBuffer = imageBuffer;
        this.proxySettings = copySettings(proxySettings);
        this.providerName = providerName;
    }

    private void checkTextureUploaded() {
        if (!textureUploaded && bufferedImage != null) {
            if (textureLocation != null) {
                deleteGlTexture();
            }

            TextureUtil.uploadTextureImage(super.getGlTextureId(), bufferedImage);
            textureUploaded = true;
        }
    }

    @Override
    public int getGlTextureId() {
        checkTextureUploaded();
        return super.getGlTextureId();
    }

    public void setBufferedImage(BufferedImage bufferedImage) {
        this.bufferedImage = bufferedImage;

        if (imageBuffer != null) {
            imageBuffer.func_152634_a();
        }
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) throws IOException {
        if (bufferedImage == null && textureLocation != null) {
            super.loadTexture(resourceManager);
        }

        if (imageThread != null) {
            return;
        }

        if (cacheFile != null && cacheFile.isFile()) {
            LOGGER.debug("Loading http texture from local cache ({})", cacheFile);

            try {
                bufferedImage = ImageIO.read(cacheFile);

                if (imageBuffer != null) {
                    setBufferedImage(imageBuffer.parseUserSkin(bufferedImage));
                }
            } catch (IOException e) {
                LOGGER.error("Couldn't load skin " + cacheFile, e);
                startDownload();
            }
            return;
        }

        startDownload();
    }

    protected void startDownload() {
        imageThread = new Thread("Provider Texture Downloader #" + THREAD_COUNTER.incrementAndGet()) {

            @Override
            public void run() {
                try {
                    byte[] imageBytes = ProviderRoutedHttp.downloadBytes(
                        imageUrl,
                        proxySettings,
                        providerName,
                        10_000,
                        15_000,
                        "WawelAuth",
                        "Texture download");

                    BufferedImage downloadedImage;
                    if (cacheFile != null) {
                        FileUtils.writeByteArrayToFile(cacheFile, imageBytes);
                        downloadedImage = ImageIO.read(cacheFile);
                    } else {
                        downloadedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                    }

                    if (imageBuffer != null) {
                        downloadedImage = imageBuffer.parseUserSkin(downloadedImage);
                    }

                    setBufferedImage(downloadedImage);
                } catch (Exception e) {
                    LOGGER.error("Couldn't download routed texture", e);
                }
            }
        };
        imageThread.setDaemon(true);
        imageThread.start();
    }

    private static ProviderProxySettings settingsFor(ClientProvider provider) {
        return provider != null ? provider.getProxySettings() : null;
    }

    private static String providerName(ClientProvider provider) {
        if (provider == null || provider.getName() == null) {
            return null;
        }
        String trimmed = provider.getName()
            .trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ProviderProxySettings copySettings(ProviderProxySettings original) {
        if (original == null) {
            return null;
        }

        ProviderProxySettings copy = new ProviderProxySettings();
        copy.setEnabled(original.isEnabled());
        copy.setType(original.getType());
        copy.setHost(original.getHost());
        copy.setPort(original.getPort());
        copy.setUsername(original.getUsername());
        copy.setPassword(original.getPassword());
        return copy;
    }
}
