package org.caureq.caureqopsboard.repo;

import org.caureq.caureqopsboard.domain.Asset;
import org.caureq.caureqopsboard.domain.Metric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MetricRepo extends JpaRepository<Metric, Long> {
    Metric findTopByAssetOrderByTsDesc(Asset asset);
    List<Metric> findByAssetAndTsBetweenOrderByTsAsc(Asset asset, Instant from, Instant to);
    List<Metric> findTop200ByAssetOrderByTsDesc(Asset asset);

    /** Latest metric per asset for the given set, using max(ts). */
    @Query("select m from Metric m where m.asset in :assets and m.ts in (select max(m2.ts) from Metric m2 where m2.asset in :assets group by m2.asset)")
    List<Metric> findLatestForAssets(@Param("assets") java.util.Collection<Asset> assets);
}

