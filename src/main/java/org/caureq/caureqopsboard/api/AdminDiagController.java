package org.caureq.caureqopsboard.api;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.caureq.caureqopsboard.service.DiagnosticService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin diagnostics controller — surface quick "top N" process info for a host/VM.
 * Uses QGA when available; can fallback to local diagnostic for configured hosts.
 */
@RestController
@RequestMapping("/api/admin/diag")
@RequiredArgsConstructor
public class AdminDiagController {
    private final AssetRepo assets;
    private final DiagnosticService diag;

    @GetMapping("/host/{hostname}")
    public Map<String,Object> diagnoseHost(@PathVariable String hostname,
                                           @RequestParam(value = "top", required = false) Integer top) {
        var a = assets.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
        int n = (top == null ? 3 : Math.max(1, Math.min(top, 10)));
        return diag.diagnose(a, n);
    }
}
