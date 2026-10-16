package dev.transferlab.transferlab.transfers.integration;

import dev.transferlab.transferlab.accounts.application.port.AccountRepository;
import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.shared.domain.Money;
import dev.transferlab.transferlab.support.AccountFixtures;
import dev.transferlab.transferlab.support.PostgreSqlTestConfiguration;
import dev.transferlab.transferlab.transfers.application.CreateTransferCommand;
import dev.transferlab.transferlab.transfers.application.TransferTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(PostgreSqlTestConfiguration.class)
class ConcurrentTransferIT {

    @Autowired AccountRepository accounts;
    @Autowired TransferTransactionService transfers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private AccountFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new AccountFixtures(
                accounts,
                jdbc,
                new TransactionTemplate(transactionManager)
        );
    }

    @Test
    void onlyOneOfTwoCompetingWithdrawalsCanSucceed() throws Exception {
        Account source = fixtures.customer(1_000_000);
        Account firstDestination = fixtures.customer(0);
        Account secondDestination = fixtures.customer(0);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = newFixedThreadPool(2)) {
            var first = executor.submit(
                    () -> attempt(source, firstDestination, ready, start)
            );

            var second = executor.submit(
                    () -> attempt(source, secondDestination, ready, start)
            );

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            assertThat(
                    java.util.List.of(
                            first.get(15, TimeUnit.SECONDS),
                            second.get(15, TimeUnit.SECONDS)
                    )
            ).containsExactlyInAnyOrder("POSTED", "INSUFFICIENT_FUNDS");
        }

        assertThat(balance(source)).isEqualTo(200_000);

        assertThat(balance(firstDestination) + balance(secondDestination))
                .isEqualTo(800_000);

        Long postedTransfers = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transfers
                WHERE source_account_id = ?
                """,
                Long.class,
                source.id()
        );

        assertThat(postedTransfers).isEqualTo(1L);
    }

    private String attempt(
            Account source,
            Account destination,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("TEST_START_TIMEOUT");
        }

        try {
            transfers.execute(new CreateTransferCommand(
                    source.ownerId(),
                    source.id(),
                    destination.id(),
                    Money.kzt(800_000)
            ));

            return "POSTED";
        } catch (IllegalStateException exception) {
            if ("INSUFFICIENT_FUNDS".equals(exception.getMessage())) {
                return "INSUFFICIENT_FUNDS";
            }

            throw exception;
        }
    }

    private long balance(Account account) {
        return accounts.findById(account.id())
                .orElseThrow()
                .balance()
                .amountMinor();
    }
}
