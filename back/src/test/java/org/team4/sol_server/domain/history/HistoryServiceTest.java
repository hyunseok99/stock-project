package org.team4.sol_server.domain.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.team4.sol_server.domain.history.dto.TradeHistoryItemDTO;
import org.team4.sol_server.domain.history.dto.TradeHistoryStatsDTO;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    private static final String USER_ID = "test_user";

    @Mock
    private CashTransactionHistoryRepository cashTransactionHistoryRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @InjectMocks
    private HistoryService historyService;

    @Test
    void getHistories_withoutFilters_mergesCashAndOrdersBeforeApplyingLimit() {
        PageRequest page = PageRequest.of(0, 1);
        LocalDateTime orderedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(cashTransactionHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, page))
                .thenReturn(List.of(CashTransactionHistory.builder()
                        .transactionType("DEPOSIT").createdAt(orderedAt.plusMinutes(1)).build()));
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, page))
                .thenReturn(List.of(OrderHistory.builder().orderType("BUY").orderedAt(orderedAt).build()));

        assertThat(historyService.getHistories(USER_ID, 1))
                .extracting(TradeHistoryItemDTO::getType).containsExactly("DEPOSIT");
        assertThat(historyService.getHistories(USER_ID, 1, " aLl ", null))
                .extracting(TradeHistoryItemDTO::getType).containsExactly("DEPOSIT");
    }

    @Test
    void getHistories_withTypeAndTicker_findsMatchingOlderOrdersAndExcludesCash() {
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, PageRequest.of(0, 2)))
                .thenReturn(List.of(
                        OrderHistory.builder().orderType("SELL").ticker("AAPL").build(),
                        OrderHistory.builder().orderType("BUY").ticker("MSFT").build()));
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, PageRequest.of(1, 2)))
                .thenReturn(List.of(OrderHistory.builder().orderType("BUY").ticker("AAPL").build()));

        assertThat(historyService.getHistories(USER_ID, 2, " buy ", "aaP"))
                .extracting(TradeHistoryItemDTO::getTicker).containsExactly("AAPL");
        verifyNoInteractions(cashTransactionHistoryRepository);
    }

    @Test
    void getHistories_withCashType_filtersCashTransactions() {
        when(cashTransactionHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, PageRequest.of(0, 3)))
                .thenReturn(List.of(
                        CashTransactionHistory.builder().transactionType("DEPOSIT").build(),
                        CashTransactionHistory.builder().transactionType("WITHDRAWAL").build()));

        assertThat(historyService.getHistories(USER_ID, 3, "withdrawal", null))
                .extracting(TradeHistoryItemDTO::getType).containsExactly("WITHDRAWAL");
        assertThat(historyService.getHistories(USER_ID, 3, "withdrawal", "AAPL")).isEmpty();
        verifyNoInteractions(orderHistoryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {" aLl ", " "})
    void getHistories_withAllOrBlankType_returnsCashAndOrders(String type) {
        PageRequest page = PageRequest.of(0, 3);
        LocalDateTime orderedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(cashTransactionHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, page))
                .thenReturn(List.of(CashTransactionHistory.builder()
                        .transactionType("DEPOSIT").createdAt(orderedAt.minusMinutes(1)).build()));
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, page))
                .thenReturn(List.of(OrderHistory.builder().orderType("BUY").orderedAt(orderedAt).build()));

        assertThat(historyService.getHistories(USER_ID, 3, type, " "))
                .extracting(TradeHistoryItemDTO::getType).containsExactly("BUY", "DEPOSIT");
    }

    @Test
    void getHistories_withTickerOnly_matchesPartialTickerAcrossOrderTypes() {
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, PageRequest.of(0, 5)))
                .thenReturn(List.of(
                        OrderHistory.builder().orderType("BUY").ticker("AAPL").build(),
                        OrderHistory.builder().orderType("SELL").ticker("AAP").build(),
                        OrderHistory.builder().orderType("BUY").ticker("MSFT").build(),
                        OrderHistory.builder().orderType("SELL").build()));

        assertThat(historyService.getHistories(USER_ID, 5, null, " aP "))
                .extracting(TradeHistoryItemDTO::getTicker).containsExactly("AAPL", "AAP");
        verifyNoInteractions(cashTransactionHistoryRepository);
    }

    @Test
    void getHistories_withUnknownType_returnsEmptyWithoutQueryingRepositories() {
        assertThat(historyService.getHistories(USER_ID, 30, "UNKNOWN", null)).isEmpty();
        verifyNoInteractions(cashTransactionHistoryRepository, orderHistoryRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, 0, 201, Integer.MAX_VALUE})
    void getHistories_clampsLimitToSupportedPageSize(int limit) {
        PageRequest page = PageRequest.of(0, limit < 1 ? 1 : 200);
        when(cashTransactionHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, page))
                .thenReturn(List.of());
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, page))
                .thenReturn(List.of());

        assertThat(historyService.getHistories(USER_ID, limit)).isEmpty();
        verify(cashTransactionHistoryRepository).findByUserIdOrderByCreatedAtDesc(USER_ID, page);
        verify(orderHistoryRepository).findByUserIdOrderByOrderedAtDesc(USER_ID, page);
    }

    @Test
    void getHistoryStats_aggregatesRecentOrdersAndIgnoresMissingLatencies() {
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, PageRequest.of(0, 500)))
                .thenReturn(List.of(
                        OrderHistory.builder().status("SUCCESS").orderType("BUY").amount(100)
                                .totalLatencyMs(10).kafkaLagMs(1).build(),
                        OrderHistory.builder().status("SUCCESS").orderType("SELL").amount(200)
                                .totalLatencyMs(20).kafkaLagMs(2).build(),
                        OrderHistory.builder().status("FAIL").orderType("BUY").amount(900)
                                .totalLatencyMs(21).kafkaLagMs(2).build(),
                        OrderHistory.builder().status("FAIL").orderType("SELL").amount(800).build(),
                        OrderHistory.builder().status("FAIL").build(),
                        OrderHistory.builder().status("FAIL").build()));

        TradeHistoryStatsDTO stats = historyService.getHistoryStats(USER_ID);

        assertThat(stats.getTotalOrders()).isEqualTo(6);
        assertThat(stats.getSuccessOrders()).isEqualTo(2);
        assertThat(stats.getFailedOrders()).isEqualTo(4);
        assertThat(stats.getSuccessRate()).isEqualTo(33.33);
        assertThat(stats.getAvgTotalLatencyMs()).isEqualTo(17.0);
        assertThat(stats.getMaxTotalLatencyMs()).isEqualTo(21L);
        assertThat(stats.getAvgKafkaLagMs()).isEqualTo(1.67);
        assertThat(stats.getTotalBuyAmount()).isEqualTo(100L);
        assertThat(stats.getTotalSellAmount()).isEqualTo(200L);
    }

    @Test
    void getHistoryStats_withoutOrders_returnsZeros() {
        when(orderHistoryRepository.findByUserIdOrderByOrderedAtDesc(USER_ID, PageRequest.of(0, 500)))
                .thenReturn(List.of());

        assertThat(historyService.getHistoryStats(USER_ID))
                .usingRecursiveComparison().isEqualTo(TradeHistoryStatsDTO.builder().build());
    }
}
