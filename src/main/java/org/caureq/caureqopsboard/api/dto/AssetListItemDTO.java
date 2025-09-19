package org.caureq.caureqopsboard.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

public record AssetListItemDTO(
        String hostname, String ip, String os,
        String status, OffsetDateTime lastSeen,
        Double cpu, Double ram, Double disk
) {}
