package org.caureq.caureqopsboard.api.dto;

public record ExecResponse(int pid, int exitCode, String stdout, String stderr) {}
