package org.team4.sol_server.domain.history;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team4.sol_server.domain.history.dto.TradeHistoryItemDTO;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SOURCE_CASH = "CASH";
    private static final String SOURCE_ORDER = "ORDER";

    private final CashTransactionHistoryRepository cashTransactionHistoryRepository;
    private final OrderHistoryRepository orderHistoryRepository;

    /**
     * 유저의 입출금 + 주문 체결 히스토리를 시간 내림차순으로 통합 조회한다.
     *
     * @param userId 유저 ID
     * @param limit  최대 조회 건수 (1~200)
     */
    public List<TradeHistoryItemDTO> getHistories(String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        Pageable page = PageRequest.of(0, safeLimit);

        // 1) 각 테이블에서 최신순 N건씩 조회
        List<CashTransactionHistory> cashList =
                cashTransactionHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, page);
        List<OrderHistory> orderList =
                orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(userId, page);

        // 2) 통합 DTO 변환
        List<TradeHistoryItemDTO> merged = new ArrayList<>(cashList.size() + orderList.size());

        for (CashTransactionHistory cash : cashList) {
            merged.add(TradeHistoryItemDTO.builder()
                    .id(cash.getId())
                    .source(SOURCE_CASH)
                    .type(cash.getTransactionType())
                    .amount(cash.getAmount())
                    .status("COMPLETED") // 저장 시점 = 처리 완료 (멱등성 event_id 기반 적재)
                    .timestamp(cash.getCreatedAt() != null ? cash.getCreatedAt().format(TS_FORMATTER) : null)
                    .build());
        }

        for (OrderHistory order : orderList) {
            merged.add(TradeHistoryItemDTO.builder()
                    .id(order.getId())
                    .source(SOURCE_ORDER)
                    .type(order.getOrderType())
                    .ticker(order.getTicker())
                    .quantity(order.getQuantity())
                    .price(order.getPrice())
                    .amount(order.getAmount())
                    .status(order.getStatus())
                    .timestamp(order.getOrderedAt() != null ? order.getOrderedAt().format(TS_FORMATTER) : null)
                    .build());
        }

        // 3) 발생 시각 기준 최신순 정렬 후 limit만큼 절단
        merged.sort(Comparator.comparing(TradeHistoryItemDTO::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (merged.size() > safeLimit) {
            return new ArrayList<>(merged.subList(0, safeLimit));
        }
        return merged;
    }
}
