package com.example.bookingsystem.config;

import static org.springframework.http.HttpStatus.*;

import com.example.bookingsystem.dto.AccountDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(
                new HandlerInterceptor() {
                    public boolean preHandle(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler
                    ) {
                        response.setHeader("Cache-Control", "no-store");
                        var session = request.getSession(false);
                        var user =
                            session == null
                                ? null
                                : (AccountDto) session.getAttribute("user");
                        String path = String.valueOf(
                            request.getAttribute(
                                org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
                            )
                        );
                        String role = path.startsWith("/api/customer/")
                            ? "CUSTOMER"
                            : path.startsWith("/api/provider/")
                              ? "PROVIDER"
                              : null;
                        if (
                            role != null && user == null
                        ) throw new ResponseStatusException(
                            UNAUTHORIZED,
                            "Please sign in."
                        );
                        if (
                            role != null && !role.equals(user.role())
                        ) throw new ResponseStatusException(
                            FORBIDDEN,
                            "This account cannot perform that action."
                        );
                        if (
                            !java.util.Set.of(
                                "GET",
                                "HEAD",
                                "OPTIONS"
                            ).contains(request.getMethod())
                        ) {
                            String token = request.getHeader("X-CSRF-Token");
                            if (
                                session == null ||
                                token == null ||
                                !token.equals(session.getAttribute("csrf"))
                            ) throw new ResponseStatusException(
                                FORBIDDEN,
                                "Refresh the page and try again."
                            );
                        }
                        return true;
                    }
                }
            )
            .addPathPatterns("/api/**");
    }
}
