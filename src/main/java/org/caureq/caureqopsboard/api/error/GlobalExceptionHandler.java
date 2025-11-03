// src/main/java/.../api/error/GlobalExceptionHandler.java
package org.caureq.caureqopsboard.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.caureq.caureqopsboard.service.ProxmoxApiException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ApiError build(ErrorCode code, String msg, String cid, Map<String,Object> details) {
        return new ApiError(Instant.now(), code, msg, cid, details);
    }

    private String cid(HttpServletRequest req) {
        return req.getHeader("X-Correlation-Id");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        return ResponseEntity.badRequest().body(
                build(ErrorCode.BAD_REQUEST, "Validation error", cid(req),
                        Map.of("fieldErrors", ex.getBindingResult().getAllErrors()))
        );
    }

    @ExceptionHandler(ProxmoxApiException.class)
    public ResponseEntity<ApiError> handleProxmox(ProxmoxApiException ex,
                                                  HttpServletRequest req) {

        HttpStatus status;
        ErrorCode code;

        if (ex.status() == 596) {              // “QGA not running” côté Proxmox
            status = HttpStatus.BAD_GATEWAY;   // 502 vers le frontend
            code = ErrorCode.AGENT_NOT_RUNNING;
        } else if (ex.status() >= 500) {
            status = HttpStatus.BAD_GATEWAY;
            code = ErrorCode.PROXMOX_5XX;
        } else {
            status = HttpStatus.BAD_REQUEST;
            code = ErrorCode.PROXMOX_4XX;
        }

        return ResponseEntity.status(status).body(
                build(code, ex.getMessage(), cid(req), ex.payload())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArg(IllegalArgumentException ex,
                                                     HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                build(ErrorCode.BAD_REQUEST, ex.getMessage(), cid(req), Map.of())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                build(ErrorCode.INTERNAL_ERROR, ex.getMessage(), cid(req), Map.of())
        );
    }
}
