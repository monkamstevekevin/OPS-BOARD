package org.caureq.caureqopsboard.repo;

import org.caureq.caureqopsboard.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepo extends JpaRepository<Asset, Long> {
    Optional<Asset> findByHostnameIgnoreCase(String hostname);
}