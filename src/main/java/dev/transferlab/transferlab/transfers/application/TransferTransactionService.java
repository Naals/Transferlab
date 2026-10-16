package dev.transferlab.transferlab.transfers.application;

import dev.transferlab.transferlab.accounts.application.port.AccountRepository;
import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.transfers.application.port.TransferPostingPort;
import dev.transferlab.transferlab.transfers.domain.TransferPlanner;
import dev.transferlab.transferlab.transfers.domain.TransferPlanner.TransferPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferTransactionService {

    private final AccountRepository accounts;
    private final TransferPostingPort posting;
    private final TransferPlanner planner;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TransferTransactionService(
            AccountRepository accounts,
            TransferPostingPort posting,
            TransferPlanner planner,
            JdbcTemplate jdbc,
            Clock clock
    ) {
        this.accounts = accounts;
        this.posting = posting;
        this.planner = planner;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            timeout = 10
    )
    public TransferPlan execute(CreateTransferCommand command) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");

        UUID sourceId = command.sourceAccountId();
        UUID destinationId = command.destinationAccountId();

        // All transfer callers use the same UUID comparison.
        UUID firstId = sourceId.compareTo(destinationId) < 0
                ? sourceId
                : destinationId;

        UUID secondId = firstId.equals(sourceId)
                ? destinationId
                : sourceId;

        Account first = accounts.lockById(firstId);
        Account second = accounts.lockById(secondId);

        Account source = first.id().equals(sourceId) ? first : second;
        Account destination = first.id().equals(destinationId) ? first : second;

        TransferPlan plan = planner.plan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                command.actorId(),
                source,
                destination,
                command.amount(),
                Instant.now(clock)
        );

        posting.post(plan);

        return plan;
    }
}
