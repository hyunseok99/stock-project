package org.team4.sol_server.domain.history;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.team4.sol_server.domain.history.dto.TradeHistoryItemDTO;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Validated
public class HistoryController {

    private final HistoryService historyService;

    /**
     * GET /api/history?userId={userId}&limit={limit}
     * 유저의 입출금 + 주문 체결 통합 거래 히스토리를 최신순으로 반환한다.
     */
    @GetMapping
    public ResponseEntity<List<TradeHistoryItemDTO>> getHistories(
            @RequestParam @NotBlank(message = "userId는 필수입니다.") String userId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(historyService.getHistories(userId, limit));
    }
}
