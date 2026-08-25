package org.team4.sol_server.domain.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    /** 유저별 최근 주문 체결 내역 조회 (최신순) */
    List<OrderHistory> findByUserIdOrderByOrderedAtDesc(String userId, Pageable pageable);
}
