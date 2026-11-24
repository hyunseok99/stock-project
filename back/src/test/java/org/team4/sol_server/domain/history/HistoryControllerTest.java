package org.team4.sol_server.domain.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.team4.sol_server.domain.history.dto.TradeHistoryItemDTO;
import org.team4.sol_server.domain.history.dto.TradeHistoryStatsDTO;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    private static final String USER_ID = "test_user";

    @Mock
    private HistoryService historyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HistoryController(historyService)).build();
    }

    @Test
    void getHistories_withoutOptionalParameters_usesDefaultLimit() throws Exception {
        when(historyService.getHistories(USER_ID, 30, null, null))
                .thenReturn(List.of(TradeHistoryItemDTO.builder().source("CASH").type("DEPOSIT").build()));

        mockMvc.perform(get("/api/history").param("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("CASH"))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"));
    }

    @Test
    void getHistories_withFiltersAndLimit_passesParametersToService() throws Exception {
        when(historyService.getHistories(USER_ID, 5, "buy", "aaP"))
                .thenReturn(List.of(TradeHistoryItemDTO.builder()
                        .source("ORDER").type("BUY").ticker("AAPL").build()));

        mockMvc.perform(get("/api/history").param("userId", USER_ID)
                        .param("limit", "5").param("type", "buy").param("ticker", "aaP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("ORDER"))
                .andExpect(jsonPath("$[0].type").value("BUY"))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    void getHistories_withNonNumericLimit_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/history").param("userId", USER_ID).param("limit", "five"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(historyService);
    }

    @Test
    void getHistoryStats_returnsServiceStats() throws Exception {
        when(historyService.getHistoryStats(USER_ID)).thenReturn(TradeHistoryStatsDTO.builder()
                .totalOrders(3).successOrders(2).failedOrders(1).successRate(66.67)
                .avgTotalLatencyMs(12.5).maxTotalLatencyMs(20L).avgKafkaLagMs(1.25)
                .totalBuyAmount(100L).totalSellAmount(200L).build());

        mockMvc.perform(get("/api/history/stats").param("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.successOrders").value(2))
                .andExpect(jsonPath("$.failedOrders").value(1))
                .andExpect(jsonPath("$.successRate").value(66.67))
                .andExpect(jsonPath("$.avgTotalLatencyMs").value(12.5))
                .andExpect(jsonPath("$.maxTotalLatencyMs").value(20))
                .andExpect(jsonPath("$.avgKafkaLagMs").value(1.25))
                .andExpect(jsonPath("$.totalBuyAmount").value(100))
                .andExpect(jsonPath("$.totalSellAmount").value(200));
    }

    @Test
    void historyEndpoints_withoutUserId_returnBadRequest() throws Exception {
        mockMvc.perform(get("/api/history")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/history/stats")).andExpect(status().isBadRequest());

        verifyNoInteractions(historyService);
    }
}
