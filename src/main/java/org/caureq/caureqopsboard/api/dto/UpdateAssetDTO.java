package org.caureq.caureqopsboard.api.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateAssetDTO(
        @Nullable @Size(max = 64) String owner,
        @Nullable @Size(max = 10) List<@Size(min=1, max=32) String> tags
) {}
