package org.caureq.caureqopsboard.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record IngestDTO(
        @NotBlank String hostname,
        @NotBlank @Pattern(regexp = "^(?:\\d{1,3}\\.){3}\\d{1,3}$") String ip,
        @NotBlank String os,
        @DecimalMin("0.0") @DecimalMax("100.0") double cpu,
        @DecimalMin("0.0") @DecimalMax("100.0") double ram,
        @DecimalMin("0.0") @DecimalMax("100.0") double disk,
        Map<@NotBlank String, @NotBlank String> services // ex: {"Spooler":"up"}
) {}