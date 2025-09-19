package org.caureq.caureqopsboard.repo;

import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.domain.Metric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MetricRepo extends JpaRepository<Metric, Long> {
    Metric findTopByAssetOrderByTsDesc(Asset asset);
    List<Metric> findByAssetAndTsBetweenOrderByTsAsc(Asset asset, Instant from, Instant to);
    List<Metric> findTop200ByAssetOrderByTsDesc(Asset asset);
}

