package org.caureq.caureqopsboard.api;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.UpdateAssetDTO;
import org.caureq.caureqopsboard.service.AssetCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Asset write endpoints (owner/tags updates).
 * Pattern: small command endpoints that delegate to AssetCommandService.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetCommandController {
    private final AssetCommandService service;

    @PatchMapping("/{hostname}")
    public ResponseEntity<Void> patch(@PathVariable String hostname, @Valid @RequestBody UpdateAssetDTO body){
        service.updateOwnerAndTags(hostname, body);
        return ResponseEntity.noContent().build(); // 204: modif effectuée, pas de body
    }
}
