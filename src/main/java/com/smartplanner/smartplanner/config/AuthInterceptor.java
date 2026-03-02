package com.smartplanner.smartplanner.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    // Session key (đồng bộ với AuthApiController / AuthController)
    public static final String SESSION_USER_ID = "USER_ID";

    // Các path được phép truy cập không cần login
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth",         // REST auth
            "/auth",             // nếu bạn còn giữ web auth
            "/swagger-ui",       // swagger ui
            "/v3/api-docs",      // openapi json
            "/error"             // spring error page
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();

        // Allow static resources (nếu bạn có)
        if (path.startsWith("/css") || path.startsWith("/js") || path.startsWith("/images")
                || path.startsWith("/webjars")) {
            return true;
        }

        // Allow public prefixes
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // Check session
        HttpSession session = request.getSession(false);
        boolean loggedIn = (session != null && session.getAttribute(SESSION_USER_ID) != null);

        if (loggedIn) {
            return true;
        }

        if (path.startsWith("/api")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Unauthorized. Please login.\"}");
            return false;
        }

        response.sendRedirect("/auth/login.html");
        return false;
    }
}
