package org.team4.sol_server.domain.balance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.NotBlank;
import org.team4.sol_server.domain.balance.dto.BalanceApiRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
@Validated
public class BalanceController {
    private final BalanceService balanceService;

    /**
     * GET /api/balance?userId={userId}
     * 현재 원화 잔액을 조회한다. (Redis 우선, Key Miss 시 MariaDB fallback)
     */
    @GetMapping
    public ResponseEntity<Map<String, Long>> getBalance(
            @RequestParam @NotBlank(message = "userId는 필수입니다.") String userId) {
        long balance = balanceService.getBalance(userId);
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(@RequestBody BalanceApiRequest request) {
        balanceService.deposit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Deposit request has been accepted");
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(@RequestBody BalanceApiRequest request) {
        balanceService.withdraw(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Withdraw request has been accepted");
    }
}
