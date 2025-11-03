package org.caureq.caureqopsboard.service.alerts;

import lombok.Value;
import org.caureq.caureqopsboard.domain.AlertRecord;
import org.caureq.caureqopsboard.repo.AlertRepo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Persistent alert registry backed by JPA, with basic list/ack. */
@Component
public class AlertRegistry {
    @Value
    public static class Alert {
        String id;
        String hostname;
        String type; // CPU_HIGH, RAM_HIGH, DISK_HIGH
        String message;
        Instant ts;
        boolean acknowledged;
    }

    private final AlertRepo repo;
    public AlertRegistry(AlertRepo repo) { this.repo = repo; }

    public void add(Alert a) {
        var rec = AlertRecord.builder()
                .id(a.id)
                .hostname(a.hostname)
                .type(a.type)
                .message(a.message)
                .ts(a.ts)
                .acknowledged(a.acknowledged)
                .build();
        repo.save(rec);
    }

    public List<Alert> all() {
        return repo.findAll().stream()
                .sorted((x,y) -> y.getTs().compareTo(x.getTs()))
                .map(this::toDto)
                .toList();
    }

    public int ack(String id) {
        return repo.findById(id).map(r -> {
            if (!r.isAcknowledged()) { r.setAcknowledged(true); repo.save(r); }
            return 1;
        }).orElse(0);
    }

    private Alert toDto(AlertRecord r) {
        return new Alert(r.getId(), r.getHostname(), r.getType(), r.getMessage(), r.getTs(), r.isAcknowledged());
    }

    /** Query with optional filters and pagination (offset/limit). */
    public List<Alert> query(String host, Boolean ack, int limit, int offset) {
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 500));
        int page = Math.max(0, offset / size);
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ts"));
        var stream = (
                host != null && !host.isBlank() && ack != null ?
                        repo.findByHostnameIgnoreCaseAndAcknowledged(host, ack, p).stream() :
                host != null && !host.isBlank() ?
                        repo.findByHostnameIgnoreCase(host, p).stream() :
                ack != null ?
                        repo.findByAcknowledged(ack, p).stream() :
                        repo.findAll(p).stream()
        );
        return stream.map(this::toDto).toList();
    }
}
