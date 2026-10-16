package dev.transferlab.transferlab.accounts.application.port;

import dev.transferlab.transferlab.accounts.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    void insert(Account account);

    Optional<Account> findById(UUID accountId);

    Account lockById(UUID accountId);
}