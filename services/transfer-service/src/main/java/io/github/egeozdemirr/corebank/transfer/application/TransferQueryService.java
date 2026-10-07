package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.application.port.TransferReader;
import io.github.egeozdemirr.corebank.transfer.domain.exception.TransferNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferQueryService {

    private final TransferReader transferReader;

    public TransferQueryService(TransferReader transferReader) {
        this.transferReader = transferReader;
    }

    public Transfer getTransfer(TransferId transferId) {
        return transferReader.findById(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
