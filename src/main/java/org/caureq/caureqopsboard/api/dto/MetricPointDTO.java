package org.caureq.caureqopsboard.api.dto;

import java.time.OffsetDateTime;

public record MetricPointDTO(
        OffsetDateTime ts,   // horodatage local (affichage)
        double cpu,
        double ram,
        double disk
) {}
