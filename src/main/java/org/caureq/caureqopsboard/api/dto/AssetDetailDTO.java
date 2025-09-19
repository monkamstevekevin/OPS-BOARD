package org.caureq.caureqopsboard.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record AssetDetailDTO(
        String hostname, String ip, String os,
        String owner, List<String> tags, OffsetDateTime lastSeen
) {}
