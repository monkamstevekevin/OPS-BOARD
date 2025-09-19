package org.caureq.caureqopsboard.api;


import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.AssetDetailDTO;
import org.caureq.caureqopsboard.api.dto.AssetListItemDTO;
import org.caureq.caureqopsboard.service.AssetQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetController {
    private final AssetQueryService service;

    @GetMapping("/assets")
    public List<AssetListItemDTO> list(@RequestParam(value="q", required=false) String q){
        return service.list(q);
    }
    @GetMapping("/assets/{hostname}")
    public AssetDetailDTO get(@PathVariable String hostname){
        return service.get(hostname);
    }

}
