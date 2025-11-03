package org.caureq.caureqopsboard.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "assets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String hostname;

    @Column(length = 64)
    private String ip;

    @Column(length = 64)
    private String os;

    @Column(length = 64)
    private String owner;

    @Column(length = 256)
    private String tags; // CSV simple v1

    // Optionnel: mapping VM pour Proxmox (v1: non requis mais utile)
    @Column(length = 64)
    private String node;   // nom du node proxmox

    private Integer vmid;  // vm id proxmox

    private Instant lastSeen;
}
