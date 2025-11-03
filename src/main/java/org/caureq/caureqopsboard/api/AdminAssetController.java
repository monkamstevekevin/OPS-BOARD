package org.caureq.caureqopsboard.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
public class AdminAssetController {
    private final org.caureq.caureqopsboard.repo.AssetRepo assetRepo;

    public record MappingUpdate(@NotBlank String node, @Positive Integer vmid) {}

    @PatchMapping("/{hostname}/mapping")
    public ResponseEntity<Void> updateMapping(@PathVariable String hostname,
                                              @RequestBody @Valid MappingUpdate body) {
        var a = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));
        a.setNode(body.node());
        a.setVmid(body.vmid());
        assetRepo.save(a);
        return ResponseEntity.noContent().build();
    }
}

