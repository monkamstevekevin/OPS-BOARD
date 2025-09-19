package org.caureq.caureqopsboard.api.dto;


import java.time.OffsetDateTime;

public record MetricSummaryDTO(
        OffsetDateTime from,
        OffsetDateTime to,
        Double cpuAvg, Double cpuMax,
        Double ramAvg, Double ramMax,
        Double diskAvg, Double diskMax,
        Integer points
) {}
