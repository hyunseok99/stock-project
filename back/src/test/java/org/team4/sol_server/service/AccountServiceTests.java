//package org.team4.sol_server.service;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.transaction.annotation.Transactional;
//import org.team4.sol_server.domain.account.dto.AccountDTO;
//import org.team4.sol_server.domain.account.entity.AccountEntity;
//import org.team4.sol_server.domain.account.repository.AccountRepository;
//import org.team4.sol_server.domain.account.service.AccountService;
//import org.team4.sol_server.scheduler.InterestScheduler;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//@SpringBootTest
//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@Transactional
//public class AccountServiceTests {
//
//    @Autowired
//    private AccountRepository accountRepository;
//
//    @Autowired
//    private AccountService accountService;
//
//    private String testAccountNumber = "1101";
//    private String testAccountNumber2 = "1102";
//    @Autowired
//    private InterestScheduler interestScheduler;
//
//    @BeforeEach
//    void setUp() {
//        AccountEntity account1 = AccountEntity.builder()
//                .accountNumber(testAccountNumber)
//                .userIdx(1)
//                .balance(1000000L)
//                .investorRatio(50)
//                .interest(25000)
//                .interestRatio(2.5)
//                .build();
//        accountRepository.save(account1);
//
//        AccountEntity account2 = AccountEntity.builder()
//                .accountNumber(testAccountNumber2)
//                .userIdx(2)
//                .balance(500000L)
//                .investorRatio(30)
//                .interest(0)
//                .interestRatio(1.2)
//                .build();
//        accountRepository.save(account2);
//    }
//
//    @Test
//    public void testGetAccount() {
//        // 계좌 정보 조회 테스트
//        Optional<AccountEntity> accountOpt = accountService.getAccountByNumber(testAccountNumber);
//        assertThat(accountOpt).isPresent();
//        assertThat(accountOpt.get().getBalance()).isEqualTo(1000000);
//        System.out.println("파킹 통장 잔액: " + accountOpt.get().getBalance());
//    }
//
//    @Test
//    public void testDeposit() {
//        // 입금 테스트: 10만원 입금
//        boolean result = accountService.deposit(testAccountNumber, 100000L);
//        assertThat(result).isTrue();
//
//        // 변경된 잔액 확인
//        Optional<AccountEntity> updatedAccount = accountService.getAccountByNumber(testAccountNumber);
//        assertThat(updatedAccount).isPresent();
//        assertThat(updatedAccount.get().getBalance()).isEqualTo(1100000);
//        System.out.println("파킹 통장 잔액: " + updatedAccount.get().getBalance());
//    }
//
//    @Test
//    public void testTransfer() {
//        // 계좌 간 송금 테스트 (출금 10만원)
//        String result = accountService.transfer(testAccountNumber, testAccountNumber2, 100000L);
//        assertThat(result);
//
//        // 잔액 확인
//        Optional<AccountEntity> fromAccount = accountService.getAccountByNumber(testAccountNumber);
//        Optional<AccountEntity> toAccount = accountService.getAccountByNumber(testAccountNumber2);
//
//        assertThat(fromAccount).isPresent();
//        assertThat(toAccount).isPresent();
//        assertThat(fromAccount.get().getBalance()).isEqualTo(900000);
//        assertThat(toAccount.get().getBalance()).isEqualTo(600000);
//
//        System.out.println("파킹 통장 잔액: " + fromAccount.get().getBalance());
//        System.out.println("증권 계좌 잔액: " + toAccount.get().getBalance());
//    }
//
//    @Test
//    public void testSetTransferRatio() {
//        // 투자 비율 변경 테스트
//        accountService.setTransferRatio(testAccountNumber, 80);
//        Optional<AccountEntity> updatedAccount = accountService.getAccountByNumber(testAccountNumber);
//        assertThat(updatedAccount).isPresent();
//        assertThat(updatedAccount.get().getInvestorRatio()).isEqualTo(80);
//        System.out.println("투자 비율 변경: " + updatedAccount.get().getInvestorRatio());
//    }
//
//    @Test
//    public void testCollectInterest() {
//        // 이자 받기 기능 테스트
//        AccountDTO updatedAccount = accountService.collectInterest(testAccountNumber);
//
//        // 이자가 올바르게 추가되었는지 확인
//        assertThat(updatedAccount).isNotNull();
//        assertThat(updatedAccount.getBalance()).isEqualTo(1025000);  // 100만원 + 2.5% (25000원)
//        System.out.println("이자 받기: " + updatedAccount.getBalance());
//    }
//
//    @Test
//    public void testGetInterest() {
//        // 받을 이자 조회 테스트
//        int interest = accountService.getInterest(testAccountNumber);
//        assertThat(interest).isEqualTo(25000);  // 100만원 × 2.5% = 25000원
//        System.out.println(interest);
//    }
//    /**이자 스케줄러 테스트 **/
///*    @Test
//    public void testInterest(){
//        System.out.println(" ■■■■■■■■■■■■■■ [테스트 시작] 이자 계산 확인  ■■■■■■■■■■■■■■");
//
//        // 1. 기존 계좌 정보 저장 (applyInterest 실행 전)
//        List<AccountEntity> accountsBefore = accountRepository.findAll();
//
//        System.out.println("----- [이자 적용 전] -----");
//        for (AccountEntity account : accountsBefore) {
//            System.out.println("계좌번호: " + account.getAccountNumber()
//                    + ", 잔고: " + account.getBalance()
//                    + ", 현재 이자: " + account.getInterest());
//        }
//
//        // 2. 이자 적용 (스케줄러 실행)
//        interestScheduler.applyInterest();
//
//        // 3. applyInterest() 실행 후 업데이트된 계좌 정보 가져오기
//        List<AccountEntity> accountsAfter = accountRepository.findAll();
//        double interestRate = 0.02;  // 연 2% 이자율
//
//        for (int i = 0; i < accountsAfter.size(); i++) {
//            AccountEntity before = accountsBefore.get(i);  // 기존 계좌 정보
//            AccountEntity after = accountsAfter.get(i);    // 업데이트된 계좌 정보
//
//            long currentBalance = after.getBalance(); // 현재 잔고
//            int previousInterest = before.getInterest(); // 기존 이자
//
//            // 하루 이자 계산 (소수점 반올림)
//            int dailyInterest = (int) Math.round((currentBalance * interestRate) / 365.0);
//
//            // 예상된 최종 이자
//            int expectedInterest = previousInterest + dailyInterest;
//
//            // 검증
//            assertEquals(expectedInterest, after.getInterest(),
//                    "계좌번호: " + after.getAccountNumber() +
//                            " | 잔고: " + currentBalance +
//                            " | 예상된 이자: " + expectedInterest +
//                            " | 실제 적용된 이자: " + after.getInterest());
//
//            // 디버깅용 출력
//            System.out.println("계좌번호: " + after.getAccountNumber() +
//                    " | 기존 이자: " + previousInterest +
//                    " | 추가된 이자: " + dailyInterest +
//                    " | 최종 적용 이자: " + after.getInterest());
//        }
//
//        System.out.println("■■■■■■■■■■■■■■ [테스트 종료] 정상적으로 이자 적용 확인 완료 ■■■■■■■■■■■■■■");
//    }*/
//
//
//}
