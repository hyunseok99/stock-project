package org.team4.sol_server.domain.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashTransactionHistoryRepository extends JpaRepository<CashTransactionHistory, Long> {

    /** 유저별 최근 입출금 내역 조회 (최신순) */
    List<CashTransactionHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
