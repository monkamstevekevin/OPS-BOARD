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
}