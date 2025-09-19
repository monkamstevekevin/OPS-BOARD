package org.caureq.caureqopsboard.repo;

import org.caureq.caureqopsboard.domain.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionLogRepo extends JpaRepository<ActionLog, Long> {}
