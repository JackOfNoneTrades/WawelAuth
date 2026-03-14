package org.fentanylsolutions.wawelauth.wawelnet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.HttpMethod;

/**
 * Simple linear HTTP router with {@code {param}} path segment matching.
 * <p>
 * Under 15 routes total: no trie needed.
 */
public class HttpRouter {

    private final List<Route> routes = new ArrayList<>();

    public void get(String pattern, RouteHandler handler) {
        routes.add(new Route(HttpMethod.GET, pattern, handler));
    }

    public void post(String pattern, RouteHandler handler) {
        routes.add(new Route(HttpMethod.POST, pattern, handler));
    }

    public void put(String pattern, RouteHandler handler) {
        routes.add(new Route(HttpMethod.PUT, pattern, handler));
    }

    public void delete(String pattern, RouteHandler handler) {
        routes.add(new Route(HttpMethod.DELETE, pattern, handler));
    }

    /**
     * Matches a request method and path against registered routes.
     *
     * @return a {@link MatchResult} if a route matches, or null if no route matches
     */
    public MatchResult match(HttpMethod method, String path) {
        for (Route route : routes) {
            if (!route.method.equals(method)) {
                continue;
            }
            Map<String, String> params = tryMatch(route.segments, splitPath(path));
            if (params != null) {
                return new MatchResult(route.handler, params);
            }
        }
        return null;
    }

    private static String[] splitPath(String path) {
        // Collapse consecutive slashes (e.g. "//authserver/..." → "/authserver/...")
        // so launchers that append paths to a trailing-slash apiRoot still match.
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return new String[0];
        }
        return path.split("/", -1);
    }

    /**
     * Tries to match path segments against a route's pattern segments.
     * Returns extracted path parameters on success, null on failure.
     */
    private static Map<String, String> tryMatch(String[] patternSegments, String[] pathSegments) {
        if (patternSegments.length != pathSegments.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < patternSegments.length; i++) {
            String pat = patternSegments[i];
            String seg = pathSegments[i];
            if (pat.startsWith("{") && pat.endsWith("}")) {
                params.put(pat.substring(1, pat.length() - 1), seg);
            } else if (!pat.equals(seg)) {
                return null;
            }
        }
        return params;
    }

    public static class MatchResult {

        private final RouteHandler handler;
        private final Map<String, String> pathParams;

        MatchResult(RouteHandler handler, Map<String, String> pathParams) {
            this.handler = handler;
            this.pathParams = pathParams;
        }

        public RouteHandler getHandler() {
            return handler;
        }

        public Map<String, String> getPathParams() {
            return pathParams;
        }
    }

    private static class Route {

        final HttpMethod method;
        final String[] segments;
        final RouteHandler handler;

        Route(HttpMethod method, String pattern, RouteHandler handler) {
            this.method = method;
            this.segments = splitPath(pattern);
            this.handler = handler;
        }
    }
}
