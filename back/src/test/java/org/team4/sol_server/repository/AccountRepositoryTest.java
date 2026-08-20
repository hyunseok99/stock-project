//package org.team4.sol_server.repository;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.team4.sol_server.domain.account.entity.AccountEntity;
//import org.team4.sol_server.domain.account.repository.AccountRepository;
//
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DataJpaTest
//public class AccountRepositoryTest {
//
//    @Autowired
//    private AccountRepository accountRepository;
//
//    @Test
//    public void testFindByAccountNumber() {
//        Optional<AccountEntity> account = accountRepository.findByAccountNumber("ACC123456");
//
//        assertThat(account).isPresent();
//        assertThat(account.get().getBalance()).isEqualTo(1000000);
//    }
//}
