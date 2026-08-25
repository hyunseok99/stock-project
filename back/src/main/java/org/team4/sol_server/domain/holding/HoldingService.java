package org.team4.sol_server.domain.holding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team4.sol_server.domain.holding.dto.HoldingResponseDTO;
import org.team4.sol_server.domain.stock.LiveStock;
import org.team4.sol_server.domain.stock.LiveStockRepository;
import org.team4.sol_server.domain.user.UserService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldingService {

    private final UserStockRepository userStockRepository;
    private final UserService userService;
    private final LiveStockRepository liveStockRepository;

    /**
     * MariaDB user_stocks 테이블에서 사용자별 보유 주식 리스트를 조회한다.
     * - 존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던진다.
     * - livestock 최신 데이터 기준으로 ticker에 해당하는 종목명(name)을 함께 반환한다.
     */
    public List<HoldingResponseDTO> getHoldingsByUserId(String userId) {
        // 존재하지 않는 유저면 USER_NOT_FOUND 예외 발생
        userService.findById(userId);

        List<UserStock> stocks = userStockRepository.findByUser_UserId(userId);

        // livestock 최신 데이터에서 ticker -> 종목명 맵 구성 (종목별 조회 쿼리 N+1 방지)
        Map<String, String> tickerNameMap = liveStockRepository.findAllLatestStocks().stream()
                .filter(liveStock -> liveStock.getTicker() != null && liveStock.getTickerName() != null)
                .collect(Collectors.toMap(
                        LiveStock::getTicker,
                        LiveStock::getTickerName,
                        (a, b) -> a // 동일 ticker 중복 시 첫 번째 값 유지
                ));

        return stocks.stream()
                .map(stock -> HoldingResponseDTO.builder()
                        .ticker(stock.getTicker())
                        // 종목명 정보가 없으면 ticker로 대체
                        .name(tickerNameMap.getOrDefault(stock.getTicker(), stock.getTicker()))
                        .quantity(stock.getQuantity())
                        .purchasePrice(stock.getAveragePrice())
                        .build())
                .collect(Collectors.toList());
    }
}
