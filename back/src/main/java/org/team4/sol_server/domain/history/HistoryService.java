package org.team4.sol_server.domain.history;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team4.sol_server.domain.history.dto.TradeHistoryItemDTO;
import org.team4.sol_server.domain.history.dto.TradeHistoryStatsDTO;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

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
        return getHistories(userId, limit, null, null);
    }

    public List<TradeHistoryItemDTO> getHistories(String userId, int limit, String type, String ticker) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        String typeFilter = type == null ? "" : type.strip().toUpperCase(Locale.ROOT);
        String tickerFilter = ticker == null ? "" : ticker.strip().toUpperCase(Locale.ROOT);
        boolean allTypes = typeFilter.isEmpty() || "ALL".equals(typeFilter);

        List<CashTransactionHistory> cashList = tickerFilter.isEmpty()
                && (allTypes || "DEPOSIT".equals(typeFilter) || "WITHDRAWAL".equals(typeFilter))
                ? findMatching(safeLimit, page -> cashTransactionHistoryRepository
                        .findByUserIdOrderByCreatedAtDesc(userId, page),
                        cash -> allTypes || typeFilter.equalsIgnoreCase(cash.getTransactionType()))
                : List.of();
        List<OrderHistory> orderList = allTypes || OrderHistory.TYPE_BUY.equals(typeFilter)
                || OrderHistory.TYPE_SELL.equals(typeFilter)
                ? findMatching(safeLimit, page -> orderHistoryRepository
                        .findByUserIdOrderByOrderedAtDesc(userId, page),
                        order -> (allTypes || typeFilter.equalsIgnoreCase(order.getOrderType()))
                                && (tickerFilter.isEmpty() || (order.getTicker() != null
                                && order.getTicker().toUpperCase(Locale.ROOT).contains(tickerFilter))))
                : List.of();

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
                    .orderId(order.getOrderId())
                    .orderedAt(order.getOrderedAt() != null ? order.getOrderedAt().format(TS_FORMATTER) : null)
                    .executedAt(order.getExecutedAt() != null ? order.getExecutedAt().format(TS_FORMATTER) : null)
                    .kafkaLagMs(order.getKafkaLagMs())
                    .totalLatencyMs(order.getTotalLatencyMs())
                    .retryCount(order.getRetryCount())
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

    private static <T> List<T> findMatching(int limit, Function<Pageable, List<T>> fetchPage,
                                           Predicate<T> matches) {
        List<T> results = new ArrayList<>(limit);
        Pageable page = PageRequest.of(0, limit);
        while (results.size() < limit) {
            List<T> items = fetchPage.apply(page);
            items.stream().filter(matches).limit(limit - results.size()).forEach(results::add);
            if (items.size() < page.getPageSize()) {
                break;
            }
            page = page.next();
        }
        return results;
    }

    public TradeHistoryStatsDTO getHistoryStats(String userId) {
        List<OrderHistory> orders = orderHistoryRepository
                .findByUserIdOrderByOrderedAtDesc(userId, PageRequest.of(0, 500));
        int successOrders = 0;
        long totalBuyAmount = 0;
        long totalSellAmount = 0;
        IntSummaryStatistics totalLatency = new IntSummaryStatistics();
        IntSummaryStatistics kafkaLag = new IntSummaryStatistics();

        for (OrderHistory order : orders) {
            if (order.getTotalLatencyMs() != null) {
                totalLatency.accept(order.getTotalLatencyMs());
            }
            if (order.getKafkaLagMs() != null) {
                kafkaLag.accept(order.getKafkaLagMs());
            }
            if ("SUCCESS".equalsIgnoreCase(order.getStatus())) {
                successOrders++;
                if (OrderHistory.TYPE_BUY.equalsIgnoreCase(order.getOrderType())) {
                    totalBuyAmount += order.getAmount();
                } else if (OrderHistory.TYPE_SELL.equalsIgnoreCase(order.getOrderType())) {
                    totalSellAmount += order.getAmount();
                }
            }
        }

        int totalOrders = orders.size();
        return TradeHistoryStatsDTO.builder()
                .totalOrders(totalOrders)
                .successOrders(successOrders)
                .failedOrders(totalOrders - successOrders)
                .successRate(totalOrders == 0 ? 0.0 : roundToTwoDecimals(100.0 * successOrders / totalOrders))
                .avgTotalLatencyMs(roundToTwoDecimals(totalLatency.getAverage()))
                .maxTotalLatencyMs(totalLatency.getCount() == 0 ? 0L : totalLatency.getMax())
                .avgKafkaLagMs(roundToTwoDecimals(kafkaLag.getAverage()))
                .totalBuyAmount(totalBuyAmount)
                .totalSellAmount(totalSellAmount)
                .build();
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
