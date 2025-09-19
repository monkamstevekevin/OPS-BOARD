package org.caureq.caureqopsboard.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.caureq.caureqopsboard.domain.ActionLog;
import org.caureq.caureqopsboard.repo.ActionLogRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service @RequiredArgsConstructor
public class AuditService {
    private final ActionLogRepo repo;

    public void log(HttpServletRequest req, String actor, String action,
                    String node, Integer vmid, String details) {
        var ip = req.getRemoteAddr();
        repo.save(ActionLog.builder()
                .userIp(ip).actor(actor).action(action)
                .node(node).vmid(vmid).details(details)
                .ts(Instant.now())
                .build());
    }
}