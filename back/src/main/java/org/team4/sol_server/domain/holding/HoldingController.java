package org.team4.sol_server.domain.holding;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.team4.sol_server.domain.holding.dto.HoldingResponseDTO;

@RestController
@RequestMapping("/api")
@Validated
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    /**
     * GET /api/portfolio?userId={userId}
     * 사용자의 보유 주식 정보를 조회한다.
     */
    @GetMapping("/portfolio")
    public ResponseEntity<List<HoldingResponseDTO>> getPortfolio(
            @RequestParam @NotBlank(message = "userId는 필수입니다.") String userId) {
        List<HoldingResponseDTO> holdings = holdingService.getHoldingsByUserId(userId);
        return ResponseEntity.ok(holdings);
    }
}
