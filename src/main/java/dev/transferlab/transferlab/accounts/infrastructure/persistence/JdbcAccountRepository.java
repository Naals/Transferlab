package dev.transferlab.transferlab.accounts.infrastructure.persistence;

import dev.transferlab.transferlab.accounts.application.port.AccountRepository;
import dev.transferlab.transferlab.accounts.domain.Account;
import dev.transferlab.transferlab.shared.domain.Money;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private static final RowMapper<Account> ROW_MAPPER = (rs, rowNum) ->
            new Account(
                    rs.getObject("id", UUID.class),
                    rs.getObject("owner_id", UUID.class),
                    Money.kzt(rs.getLong("balance_minor")),
                    Account.Status.valueOf(rs.getString("status"))
            );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account account) {
        if (account.balance().amountMinor() != 0) {
            throw new IllegalArgumentException(
                    "New customer accounts must start with zero balance"
            );
        }

        jdbc.update("""
                INSERT INTO accounts (
                    id, owner_id, kind, currency, balance_minor, status
                )
                VALUES (
                    :id, :ownerId, 'CUSTOMER', 'KZT', 0, :status
                )
                """,
                Map.of(
                        "id", account.id(),
                        "ownerId", account.ownerId(),
                        "status", account.status().name()
                )
        );
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return jdbc.query("""
                SELECT id, owner_id, balance_minor, status
                FROM accounts
                WHERE id = :id
                  AND kind = 'CUSTOMER'
                """,
                Map.of("id", accountId),
                ROW_MAPPER
        ).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Account lockById(UUID accountId) {
        return jdbc.query("""
                SELECT id, owner_id, balance_minor, status
                FROM accounts
                WHERE id = :id
                  AND kind = 'CUSTOMER'
                FOR UPDATE
                """,
                Map.of("id", accountId),
                ROW_MAPPER
        ).stream().findFirst().orElseThrow(
                () -> new NoSuchElementException("ACCOUNT_NOT_FOUND")
        );
    }
}