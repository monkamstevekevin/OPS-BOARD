package org.caureq.caureqopsboard.api;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.MetricPointDTO;
import org.caureq.caureqopsboard.api.dto.MetricSummaryDTO;
import org.caureq.caureqopsboard.service.MetricQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Metrics read APIs for an asset: latest window, ranges and summaries.
 */
@RestController
@RequestMapping("/api/assets/{hostname}/metrics")
@RequiredArgsConstructor
public class MetricController {
    private final MetricQueryService metricQueryService;

    @GetMapping
    public List<MetricPointDTO> query(
            @PathVariable String hostname,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        if (from != null && to != null) {
            return metricQueryService.between(hostname, from, to);
        }
        var lim = (limit == null ? 20 : limit);
        return metricQueryService.latest(hostname, lim);
    }

    @GetMapping("/summary")
    public MetricSummaryDTO summary(
            @PathVariable String hostname,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return metricQueryService.summary(hostname, from, to);
    }

    @GetMapping("/fs")
    public java.util.List<java.util.Map<String,Object>> fs(@PathVariable String hostname) {
        var map = metricQueryService.latestServices(hostname);
        java.util.List<java.util.Map<String,Object>> list = new java.util.ArrayList<>();
        for (var e : map.entrySet()) {
            if (e.getKey().startsWith("fs:")) {
                String mp = e.getKey().substring(3);
                Double pct = null;
                try { pct = Double.parseDouble(e.getValue()); } catch (Exception ignored) {}
                java.util.Map<String,Object> item = new java.util.HashMap<>();
                item.put("mount", mp);
                item.put("usedPct", pct);
                list.add(item);
            }
        }
        // sort: root/C: first, then others alpha
        list.sort((a,b)->{
            String ma=(String)a.get("mount"), mb=(String)b.get("mount");
            if ("/".equals(ma) || "C:\\".equals(ma)) return -1;
            if ("/".equals(mb) || "C:\\".equals(mb)) return 1;
            return ma.compareToIgnoreCase(mb);
        });
        return list;
    }
}
