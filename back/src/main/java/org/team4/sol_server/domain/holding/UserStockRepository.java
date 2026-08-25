package org.team4.sol_server.domain.holding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStockRepository extends JpaRepository<UserStock, Long> {

    /**
     * 특정 사용자의 보유 주식 전체 조회
     * SELECT u FROM UserStock u WHERE u.user.userId = :userId
     */
    List<UserStock> findByUser_UserId(String userId);

    /**
     * 특정 사용자의 특정 종목 보유 내역 단건 조회 (uq_user_ticker 기준)
     */
    Optional<UserStock> findByUser_UserIdAndTicker(String userId, String ticker);

    /**
     * 특정 사용자의 특정 종목 보유 내역 삭제
     */
    void deleteByUser_UserIdAndTicker(String userId, String ticker);
}
