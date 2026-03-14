package org.fentanylsolutions.wawelauth.wawelnet;

import java.util.LinkedHashMap;
import java.util.Map;

import org.fentanylsolutions.fentlib.services.http.ReverseProxyHttpHandler;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelserver.WawelServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

/**
 * Dispatches fully-aggregated HTTP requests through the {@link HttpRouter}.
 * Handles response serialization (JSON, binary, 204) and error mapping.
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String ALI_HEADER = "X-Authlib-Injector-API-Location";
    private static final String CSP_HEADER = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; "
        + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .create();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        WawelAuth.debug(
            "HTTP " + request.getMethod()
                + " "
                + request.getUri()
                + " from "
                + ctx.channel()
                    .remoteAddress());

        WawelServer server = WawelServer.instance();
        if (server == null) {
            sendJson(
                ctx,
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                "{\"error\":\"ServerUnavailable\",\"errorMessage\":\"Server module not initialized\"}");
            return;
        }

        HttpRouter router = server.getRouter();
        String path = new QueryStringDecoder(request.getUri()).path();

        HttpRouter.MatchResult match = router.match(request.getMethod(), path);
        if (match == null) {
            if (ReverseProxyHttpHandler.tryHandle(ctx, request)) {
                return;
            }
            sendJson(
                ctx,
                HttpResponseStatus.NOT_FOUND,
                "{\"error\":\"NotFoundOperationException\",\"errorMessage\":\"Route not found\"}");
            return;
        }

        try {
            RequestContext reqCtx = new RequestContext(
                request,
                match.getPathParams(),
                ctx.channel()
                    .remoteAddress());
            Object result = match.getHandler()
                .handle(reqCtx);

            if (result == null) {
                sendNoContent(ctx);
            } else if (result instanceof BinaryResponse) {
                sendBinary(ctx, (BinaryResponse) result);
            } else {
                sendJson(ctx, HttpResponseStatus.OK, GSON.toJson(result));
            }
        } catch (NetException e) {
            WawelAuth.debug(
                "HTTP " + e.getHttpStatus()
                    .code() + " " + e.getErrorType() + ": " + e.getErrorMessage());
            Map<String, String> errorBody = new LinkedHashMap<>();
            errorBody.put("error", e.getErrorType());
            errorBody.put("errorMessage", e.getErrorMessage());
            sendJson(ctx, e.getHttpStatus(), GSON.toJson(errorBody));
        } catch (Exception e) {
            WawelAuth.LOG.warn(
                "Unhandled exception in route handler for {} {}: {}",
                request.getMethod(),
                path,
                e.getMessage(),
                e);
            sendJson(
                ctx,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                "{\"error\":\"InternalError\",\"errorMessage\":\"Internal server error\"}");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        WawelAuth.LOG.warn(
            "HTTP handler exception from {}: {}",
            ctx.channel()
                .remoteAddress(),
            cause.getMessage());
        try {
            sendJson(
                ctx,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                "{\"error\":\"InternalError\",\"errorMessage\":\"Internal server error\"}");
        } catch (Exception e) {
            ctx.close();
        }
    }

    private static void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        ByteBuf content = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers()
            .set(HttpHeaders.Names.CONTENT_TYPE, CONTENT_TYPE_JSON);
        response.headers()
            .set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        setCommonHeaders(response);
        ctx.writeAndFlush(response)
            .addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendNoContent(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        setCommonHeaders(response);
        ctx.writeAndFlush(response)
            .addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendBinary(ChannelHandlerContext ctx, BinaryResponse binary) {
        ByteBuf content = Unpooled.wrappedBuffer(binary.getData());
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        response.headers()
            .set(HttpHeaders.Names.CONTENT_TYPE, binary.getContentType());
        response.headers()
            .set(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
        for (Map.Entry<String, String> header : binary.getHeaders()
            .entrySet()) {
            response.headers()
                .set(header.getKey(), header.getValue());
        }
        setCommonHeaders(response);
        ctx.writeAndFlush(response)
            .addListener(ChannelFutureListener.CLOSE);
    }

    private static void setCommonHeaders(FullHttpResponse response) {
        WawelServer server = WawelServer.instance();
        String aliLocation = server != null ? server.getApiLocationHeaderValue() : "/";
        response.headers()
            .set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
        response.headers()
            .set(ALI_HEADER, aliLocation);
        response.headers()
            .set("X-Content-Type-Options", "nosniff");
        response.headers()
            .set("X-Frame-Options", "DENY");
        response.headers()
            .set("Referrer-Policy", "no-referrer");
        response.headers()
            .set("Content-Security-Policy", CSP_HEADER);
    }
}
