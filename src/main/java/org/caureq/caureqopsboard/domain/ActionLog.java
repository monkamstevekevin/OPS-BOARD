package org.caureq.caureqopsboard.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "action_log", indexes = {
        @Index(name="idx_action_ts", columnList = "ts DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userIp;
    private String actor;     // ex: "admin-api"
    private String action;    // ex: "start", "shutdown", "exec"
    private String node;      // proxmox node
    private Integer vmid;     // target vm
    @Column(length=2000)
    private String details;   // JSON/texte (UPID, cmd, status…)
    private Instant ts;
}
