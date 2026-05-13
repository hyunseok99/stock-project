package org.team4.sol_server.domain.stock.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockIndexRepository extends JpaRepository<StockIndexEntity, Long> {
}
