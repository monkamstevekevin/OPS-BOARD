package org.caureq.caureqopsboard.api;


import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.AssetDetailDTO;
import org.caureq.caureqopsboard.api.dto.AssetListItemDTO;
import org.caureq.caureqopsboard.service.AssetQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Asset read APIs (query side).
 *
 * Responsibilities
 * - Serve fast, DB-backed lists and details for assets (no live Proxmox calls here).
 * - Defer live VM state (power/GA/IP) to the LiveStatus cache exposed via StatusController.
 *
 * Patterns
 * - Layered architecture (Controller -> Service -> Repository)
 * - CQRS-inspired separation: read endpoints avoid side effects and remote calls.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {
    private final AssetQueryService service;

    /**
     * List assets with optional search and pagination.
     * This returns DB information only; the UI overlays live state from /api/status/live.
     *
     * @param q optional hostname filter (contains, case-insensitive)
     * @param limit page size (default 200, max 1000)
     * @param offset start offset (0-based)
     * @param includeRetired whether to include assets tagged as "retired"
     * @return list of AssetListItemDTO for the requested slice
     */
    @GetMapping("/assets")
    public List<AssetListItemDTO> list(
            @RequestParam(value="q", required=false) String q,
            @RequestParam(value="limit", required=false) Integer limit,
            @RequestParam(value="offset", required=false) Integer offset,
            @RequestParam(value="includeRetired", required=false, defaultValue = "false") boolean includeRetired){
        return service.list(q, limit, offset, includeRetired);
    }
    /**
     * Get the DB details for an asset (owner, tags, lastSeen...)
     * Live VM state is available separately under /api/status/live/{hostname}.
     */
    @GetMapping("/assets/{hostname}")
    public AssetDetailDTO get(@PathVariable String hostname){
        return service.get(hostname);
    }

}
