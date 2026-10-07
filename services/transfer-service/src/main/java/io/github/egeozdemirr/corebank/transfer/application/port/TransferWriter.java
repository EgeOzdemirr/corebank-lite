package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import java.time.LocalDate;

/** Stores transfers. {@link #update} must fail if another transaction changed the transfer first (optimistic lock). */
public interface TransferWriter {

    /** {@code businessDay}: the day whose daily limit the transfer's amount was reserved against. */
    void add(Transfer transfer, LocalDate businessDay);

    void update(Transfer transfer);
}
