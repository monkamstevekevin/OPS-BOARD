package org.caureq.caureqopsboard.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.service.InventoryDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin endpoints to preview/apply Proxmox VM to Asset mapping. */
@RestController
@RequestMapping("/api/admin/discovery")
@RequiredArgsConstructor
public class AdminDiscoveryController {
    private final InventoryDiscoveryService svc;

    @GetMapping("/preview")
    public InventoryDiscoveryService.PreviewResult preview() {
        return svc.preview();
    }

    public record ApplyRequest(@Valid List<InventoryDiscoveryService.Proposal> proposals) {}
    public record ClearMissingRequest(@Valid List<String> hostnames) {}
    public record ArchiveMissingRequest(@Valid List<String> hostnames) {}

    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody @Valid ApplyRequest body) {
        int count = svc.apply(body.proposals());
        return ResponseEntity.ok().body(java.util.Map.of("updated", count));
    }

    @PostMapping("/clear-missing")
    public ResponseEntity<?> clearMissing(@RequestBody @Valid ClearMissingRequest body) {
        int count = svc.clearMappings(body.hostnames());
        return ResponseEntity.ok().body(java.util.Map.of("updated", count));
    }

    @PostMapping("/archive-missing")
    public ResponseEntity<?> archiveMissing(@RequestBody @Valid ArchiveMissingRequest body) {
        int count = svc.archiveMissing(body.hostnames());
        return ResponseEntity.ok().body(java.util.Map.of("updated", count));
    }
}
