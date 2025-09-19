package org.caureq.caureqopsboard.service;

import java.util.Map;

public class ProxmoxApiException extends RuntimeException {
    private final int status;
    private final Map<String, Object> payload;

    public ProxmoxApiException(int status, String message, Map<String,Object> payload) {
        super(message);
        this.status = status;
        this.payload = payload;
    }

    public int status() { return status; }
    public Map<String,Object> payload() { return payload; }
}