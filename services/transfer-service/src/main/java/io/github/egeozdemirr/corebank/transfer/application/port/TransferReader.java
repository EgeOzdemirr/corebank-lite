package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.util.Optional;

public interface TransferReader {

    Optional<Transfer> findById(TransferId transferId);
}
