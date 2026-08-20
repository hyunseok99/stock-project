package org.team4.sol_server.domain.fail;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
}
