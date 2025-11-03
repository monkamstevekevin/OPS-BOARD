package org.caureq.caureqopsboard.repo;

import org.caureq.caureqopsboard.domain.AlertRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepo extends JpaRepository<AlertRecord, String> {
    Page<AlertRecord> findAll(Pageable pageable);
    Page<AlertRecord> findByHostnameIgnoreCase(String hostname, Pageable pageable);
    Page<AlertRecord> findByAcknowledged(boolean acknowledged, Pageable pageable);
    Page<AlertRecord> findByHostnameIgnoreCaseAndAcknowledged(String hostname, boolean acknowledged, Pageable pageable);
}
