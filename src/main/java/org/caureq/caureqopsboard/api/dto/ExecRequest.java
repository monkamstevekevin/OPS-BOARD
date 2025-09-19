// src/main/java/org/caureq/caureqopsboard/api/dto/ExecRequest.java
package org.caureq.caureqopsboard.api.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ExecRequest(
        @NotNull @Size(min = 1, message = "command must have at least the program")
        List<@NotBlank String> command,

        String input,

        @Min(1) @Max(300)
        Integer timeoutSec
) {}
