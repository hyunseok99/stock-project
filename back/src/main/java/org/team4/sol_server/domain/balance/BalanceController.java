package org.team4.sol_server.domain.balance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.team4.sol_server.domain.balance.dto.BalanceApiRequest;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {
    private final BalanceService balanceService;

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
