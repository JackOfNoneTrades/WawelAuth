package org.fentanylsolutions.wawelauth.wawelserver;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;
import org.junit.Assert;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class AdminWebServiceHttpsDetectionTest {

    @Test
    public void nativeTlsIsHttps() throws Exception {
        Assert.assertTrue(isHttpsRequest("203.0.113.9", true, null, null, null));
    }

    @Test
    public void directRemoteCannotSpoofForwardedProto() throws Exception {
        Assert.assertFalse(isHttpsRequest("203.0.113.9", false, "https", null, null));
    }

    @Test
    public void loopbackProxyAcceptsExactForwardedHttpsProto() throws Exception {
        Assert.assertTrue(isHttpsRequest("127.0.0.1", false, "https", null, null));
        Assert.assertTrue(isHttpsRequest("::1", false, "HTTPS", null, null));
    }

    @Test
    public void loopbackProxyRejectsAmbiguousForwardedProtoChain() throws Exception {
        Assert.assertFalse(isHttpsRequest("127.0.0.1", false, "http, https", null, null));
    }

    @Test
    public void loopbackProxyRejectsSubstringForwardedProto() throws Exception {
        Assert.assertFalse(isHttpsRequest("127.0.0.1", false, "nothttps", null, null));
    }

    @Test
    public void loopbackProxyStillAcceptsLegacyHttpsHeaders() throws Exception {
        Assert.assertTrue(isHttpsRequest("127.0.0.1", false, null, "on", null));
        Assert.assertTrue(isHttpsRequest("127.0.0.1", false, null, null, "1"));
    }

    private static boolean isHttpsRequest(String remoteIp, boolean secureTransport, String forwardedProto,
        String frontEndHttps, String forwardedSsl) throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/api/wawelauth/admin/bootstrap");
        if (forwardedProto != null) {
            request.headers()
                .set("X-Forwarded-Proto", forwardedProto);
        }
        if (frontEndHttps != null) {
            request.headers()
                .set("Front-End-Https", frontEndHttps);
        }
        if (forwardedSsl != null) {
            request.headers()
                .set("X-Forwarded-Ssl", forwardedSsl);
        }

        RequestContext ctx = new RequestContext(
            request,
            Collections.emptyMap(),
            new InetSocketAddress(InetAddress.getByName(remoteIp), 12345),
            secureTransport);
        Method method = AdminWebService.class.getDeclaredMethod("isHttpsRequest", RequestContext.class);
        method.setAccessible(true);
        return ((Boolean) method.invoke(null, ctx)).booleanValue();
    }
}
