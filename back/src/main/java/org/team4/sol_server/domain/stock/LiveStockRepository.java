package org.team4.sol_server.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.team4.sol_server.domain.stock.dto.TickerDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveStockRepository extends JpaRepository<LiveStock, Long> {

    @Query("SELECT MIN(s.date) FROM LiveStock s WHERE s.ticker = :ticker")
    Optional<LocalDate> findMinDateByTicker(@Param("ticker") String ticker);


    // 티커를 이용해서 ( startDate , endDate ] 의 스톡 데이터 조회
    @Query("SELECT s FROM LiveStock s WHERE s.ticker = :ticker AND s.date >= :startDate AND s.date < :endDate")
    List<LiveStock> findByTickerAndDateRangeExclusive(
            @Param("ticker") String ticker, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate
    );

    // 모든 티커 조회
    @Query("SELECT DISTINCT s.ticker FROM LiveStock s order by s.tickerName desc")
    List<String> findDistinctTickers();

    LiveStock findFirstByTickerOrderByDateDesc(String ticker);

    @Query("SELECT s FROM LiveStock s " +
       "WHERE s.id IN (SELECT MAX(s2.id) FROM LiveStock s2 GROUP BY s2.ticker)")
    List<LiveStock> findAllLatestStocks();
}
