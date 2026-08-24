package com.intellimove.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rewrites the Accept header for /actuator/prometheus requests.
 * Prometheus 2.49+ sends 'application/openmetrics-text' which Spring Boot
 * doesn't handle properly, causing the WelcomePageHandlerMapping to return 406.
 * This filter normalizes the Accept header to standard text/plain.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrometheusAcceptFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path != null && path.contains("/actuator/prometheus")) {
            String accept = request.getHeader("Accept");
            log.debug("PrometheusAcceptFilter: path={}, original Accept={}", path, accept);

            if (accept != null && accept.contains("openmetrics-text")) {
                log.debug("PrometheusAcceptFilter: rewriting Accept header from openmetrics to text/plain");
                filterChain.doFilter(new AcceptRewriteRequestWrapper(request, "text/plain, */*;q=0.1"), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * HttpServletRequestWrapper that overrides the Accept header value.
     */
    static class AcceptRewriteRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final String overriddenAccept;

        AcceptRewriteRequestWrapper(HttpServletRequest request, String overriddenAccept) {
            super(request);
            this.overriddenAccept = overriddenAccept;
        }

        @Override
        public String getHeader(String name) {
            if ("Accept".equalsIgnoreCase(name)) {
                return overriddenAccept;
            }
            return super.getHeader(name);
        }
    }
}
