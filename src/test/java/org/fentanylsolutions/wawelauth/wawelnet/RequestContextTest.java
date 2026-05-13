package org.fentanylsolutions.wawelauth.wawelnet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class RequestContextTest {

    @Test
    public void directRemoteIgnoresForwardedHeaders() throws Exception {
        RequestContext ctx = context("203.0.113.9", "198.51.100.20", "198.51.100.21");

        Assert.assertEquals("203.0.113.9", ctx.getRemoteIp());
        Assert.assertEquals("203.0.113.9", ctx.getClientIp());
    }

    @Test
    public void loopbackProxyUsesXRealIp() throws Exception {
        RequestContext ctx = context("127.0.0.1", "198.51.100.20", "198.51.100.21");

        Assert.assertEquals("127.0.0.1", ctx.getRemoteIp());
        Assert.assertEquals("198.51.100.20", ctx.getClientIp());
    }

    @Test
    public void loopbackProxyUsesRightmostForwardedForAddress() throws Exception {
        RequestContext ctx = context("127.0.0.1", null, "198.51.100.10, 198.51.100.11");

        Assert.assertEquals("198.51.100.11", ctx.getClientIp());
    }

    @Test
    public void invalidForwardedHeadersFallBackToRemoteIp() throws Exception {
        RequestContext ctx = context("127.0.0.1", "not-an-ip", "also-not-an-ip");

        Assert.assertEquals("127.0.0.1", ctx.getClientIp());
    }

    @Test
    public void forwardedIpv4PortIsAccepted() throws Exception {
        RequestContext ctx = context("127.0.0.1", "198.51.100.20:12345", null);

        Assert.assertEquals("198.51.100.20", ctx.getClientIp());
    }

    private static RequestContext context(String remoteIp, String realIp, String forwardedFor) throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        if (realIp != null) {
            request.headers()
                .set("X-Real-IP", realIp);
        }
        if (forwardedFor != null) {
            request.headers()
                .set("X-Forwarded-For", forwardedFor);
        }

        return new RequestContext(
            request,
            Collections.emptyMap(),
            new InetSocketAddress(InetAddress.getByName(remoteIp), 12345),
            false);
    }
}
