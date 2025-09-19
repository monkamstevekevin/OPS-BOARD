package org.caureq.caureqopsboard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.config.AppProps;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {
    private final AppProps props; // voir step 3bis ci-dessous

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();       // ex: "/api/assets/pve01"
        String method = req.getMethod();         // ex: "PATCH"

        boolean needsKey =
                path.startsWith("/api/ingest")
                        || (path.startsWith("/api/assets/") && "PATCH".equalsIgnoreCase(method));

        if (needsKey) {
            String key = req.getHeader("X-API-KEY");
            if (key == null || !key.equals(props.apiKey())) {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Missing or invalid X-API-KEY\"}");
                return; // on bloque ici
            }
        }

        chain.doFilter(req, res); // on laisse passer vers les controllers
    }
}
