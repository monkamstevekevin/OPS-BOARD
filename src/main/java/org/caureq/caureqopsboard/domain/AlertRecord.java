package org.caureq.caureqopsboard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_ts", columnList = "ts DESC"),
        @Index(name = "idx_alert_host_ts", columnList = "hostname, ts DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlertRecord {
    @Id
    @Column(length = 36)
    private String id; // UUID string

    @Column(nullable = false, length = 128)
    private String hostname;

    @Column(nullable = false, length = 64)
    private String type; // CPU_HIGH, RAM_HIGH, DISK_HIGH

    @Column(nullable = false, length = 512)
    private String message;

    @Column(nullable = false)
    private Instant ts;

    @Column(nullable = false)
    private boolean acknowledged;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (ts == null) ts = Instant.now();
    }
}

