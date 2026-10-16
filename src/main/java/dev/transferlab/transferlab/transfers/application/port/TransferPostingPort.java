package dev.transferlab.transferlab.transfers.application.port;

import dev.transferlab.transferlab.transfers.domain.TransferPlanner.TransferPlan;

public interface TransferPostingPort {

    void post(TransferPlan plan);
}
