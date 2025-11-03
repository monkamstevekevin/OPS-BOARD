package org.caureq.caureqopsboard.api;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.service.alerts.AlertRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Alerts read controller — list and filter alerts.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertsController {
    private final AlertRegistry registry;

    @GetMapping
    public java.util.List<AlertRegistry.Alert> list(
            @RequestParam(value = "host", required = false) String host,
            @RequestParam(value = "ack", required = false) Boolean ack,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset
    ) {
        int lim = (limit == null ? 50 : limit);
        int off = (offset == null ? 0 : offset);
        return registry.query(host, ack, lim, off);
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<?> ack(@PathVariable String id) {
        int n = registry.ack(id);
        return ResponseEntity.ok(java.util.Map.of("acknowledged", n));
    }
}
