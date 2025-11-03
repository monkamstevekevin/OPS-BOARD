package org.caureq.caureqopsboard.api;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.service.LiveStatusService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {
    private final LiveStatusService live;

    @GetMapping("/live")
    public List<LiveStatusService.LiveStatus> all() { return live.all(); }

    @GetMapping("/live/{hostname}")
    public LiveStatusService.LiveStatus one(@PathVariable String hostname) {
        return live.get(hostname).orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
    }
}

