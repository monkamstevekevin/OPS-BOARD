package org.caureq.caureqopsboard.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "metrics", indexes = {
        @Index(name = "idx_metric_asset_ts", columnList = "asset_id, ts DESC")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Metric {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    private double cpu;   // %
    private double ram;   // %
    private double disk;  // % libre (ou utilisé si tu préfères, mais sois cohérent)

    @Column(columnDefinition = "text")
    private String services; // v1: "nginx:up;docker:down" (JSON à venir)

    @Column(nullable = false)
    private Instant ts;   // horodatage serveur
}
