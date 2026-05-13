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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class AdminWebServiceSessionTokenTest {

    @Test
    public void cookieSessionTokenIsIgnored() throws Exception {
        FullHttpRequest request = request();
        request.headers()
            .set(HttpHeaders.Names.COOKIE, "other=value; wawelauth_admin_session=cookie-token");

        Assert.assertNull(extractSessionToken(request));
    }

    @Test
    public void bearerTokenIsPreferredOverCookie() throws Exception {
        FullHttpRequest request = request();
        request.headers()
            .set(HttpHeaders.Names.AUTHORIZATION, "Bearer bearer-token");
        request.headers()
            .set(HttpHeaders.Names.COOKIE, "wawelauth_admin_session=cookie-token");

        Assert.assertEquals("bearer-token", extractSessionToken(request));
    }

    @Test
    public void legacyAdminHeaderIsStillAccepted() throws Exception {
        FullHttpRequest request = request();
        request.headers()
            .set("X-WawelAuth-Admin-Session", "header-token");

        Assert.assertEquals("header-token", extractSessionToken(request));
    }

    private static FullHttpRequest request() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/wawelauth/admin/session");
    }

    private static String extractSessionToken(FullHttpRequest request) throws Exception {
        RequestContext ctx = new RequestContext(
            request,
            Collections.emptyMap(),
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345),
            true);
        Method method = AdminWebService.class.getDeclaredMethod("extractSessionToken", RequestContext.class);
        method.setAccessible(true);
        return (String) method.invoke(null, ctx);
    }
}
