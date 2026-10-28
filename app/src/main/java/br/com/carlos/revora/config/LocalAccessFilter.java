package br.com.carlos.revora.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** Restrict the desktop server to its own browser origin, including DNS rebinding. */
@Component
@Profile("local")
public class LocalAccessFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String host = req.getHeader("Host");
        Set<String> hosts = Set.of("127.0.0.1:" + req.getLocalPort(), "localhost:" + req.getLocalPort());
        String origin = req.getHeader("Origin");
        if (!hosts.contains(host == null ? "" : host)
                || (origin != null && !origin.equals("http://" + host))
                || (!Set.of("GET", "HEAD").contains(req.getMethod())
                    && !"1".equals(req.getHeader("X-Revora-Local")))) {
            res.sendError(403);
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", "default-src 'self'; connect-src 'self'; img-src 'self' blob:; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        chain.doFilter(req, res);
    }
}
