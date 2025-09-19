package org.caureq.caureqopsboard.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;


@Component
public class ApiKeyAdminFilter implements Filter {
    private final String adminKey;
    private final List<String> cidrs;

    private final List<String> protectedPrefixes = List.of(
            "/api/admin/vm/", "/api/admin/exec/"
    );

    public ApiKeyAdminFilter(org.springframework.core.env.Environment env) {
        this.adminKey = env.getProperty("admin.api-key", "ADMIN-CHANGE-ME");
        var raw = env.getProperty("admin.allow-ips", "127.0.0.1");
        this.cidrs = List.of(raw.split("\\s*,\\s*"));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var r = (HttpServletRequest) req;
        var w = (HttpServletResponse) res;

        var path = r.getRequestURI();
        boolean adminRoute = protectedPrefixes.stream().anyMatch(path::startsWith);
        if (!adminRoute) { chain.doFilter(req, res); return; }

        // 1) Clé admin
        String k = r.getHeader("X-ADMIN-API-KEY");
        if (k == null || !k.equals(adminKey)) {
            w.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing/invalid admin key");
            return;
        }

        // 2) IP allowlist
        String ip = r.getRemoteAddr();
        // normalise loopback IPv6 -> IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";

        if (!isAllowed(ip)) {
            w.sendError(HttpServletResponse.SC_FORBIDDEN, "IP not allowed: " + ip);
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isAllowed(String ip) {
        for (var rule : cidrs) {
            if (rule.equals("*")) return true;
            if (!rule.contains("/")) {
                if (rule.equals(ip)) return true;           // IP exacte (ex: 127.0.0.1)
            } else {
                if (matchesCidr(ip, rule)) return true;     // IPv4 CIDR (ex: 192.168.1.0/24)
            }
        }
        return false;
    }

    // IPv4 uniquement (suffisant pour notre cas)
    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String base = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            byte[] addr = InetAddress.getByName(ip).getAddress();
            byte[] net  = InetAddress.getByName(base).getAddress();
            if (addr.length != 4 || net.length != 4) return false; // on ne gère qu’IPv4 ici

            int mask = prefix == 0 ? 0 : 0xffffffff << (32 - prefix);
            int a = byteArrayToInt(addr);
            int n = byteArrayToInt(net);
            return (a & mask) == (n & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static int byteArrayToInt(byte[] b) {
        return ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16) | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
    }
}

