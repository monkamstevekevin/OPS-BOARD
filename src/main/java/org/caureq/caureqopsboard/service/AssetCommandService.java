package org.caureq.caureqopsboard.service;

import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.api.dto.UpdateAssetDTO;
import org.caureq.caureqopsboard.repo.AssetRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetCommandService {
    private final AssetRepo assetRepo;

    @Transactional
    public void updateOwnerAndTags(String hostname, UpdateAssetDTO d){
        var asset = assetRepo.findByHostnameIgnoreCase(hostname)
                .orElseThrow(() -> new IllegalArgumentException("asset not found: " + hostname));

        if (d.owner() != null) {
            asset.setOwner(d.owner().trim());
        }

        if (d.tags() != null) {
            var normalized = d.tags().stream()
                    .map(t -> t == null ? "" : t.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new)); // garde l’ordre, déduplique
            var csv = String.join(",", normalized);
            asset.setTags(csv);
        }

        assetRepo.save(asset); // idempotent: mêmes valeurs => même état
    }
}
