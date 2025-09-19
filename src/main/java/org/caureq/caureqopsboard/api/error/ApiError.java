
package org.caureq.caureqopsboard.api.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        ErrorCode code,
        String message,
        String correlationId,
        Map<String,Object> details
) { }
