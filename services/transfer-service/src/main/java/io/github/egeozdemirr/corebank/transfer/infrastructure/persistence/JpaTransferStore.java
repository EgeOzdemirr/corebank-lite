package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.application.port.TransferLocker;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReader;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferWriter;
import io.github.egeozdemirr.corebank.transfer.domain.exception.TransferNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaTransferStore implements TransferReader, TransferWriter, TransferLocker {

    private final TransferJpaRepository repository;

    JpaTransferStore(TransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Transfer> findById(TransferId transferId) {
        return repository.findById(transferId.value()).map(TransferJpaEntity::toDomain);
    }

    @Override
    public Optional<Transfer> lockById(TransferId transferId) {
        return repository.findAndLockById(transferId.value()).map(TransferJpaEntity::toDomain);
    }

    /** Flushed at once so that the daily usage release, which reads the row by id over JDBC, can see it. */
    @Override
    public void add(Transfer transfer, LocalDate businessDay) {
        repository.saveAndFlush(TransferJpaEntity.newFrom(transfer, businessDay));
    }

    /** Changes the managed entity; Hibernate issues {@code UPDATE ... WHERE version = ?} at flush. */
    @Override
    public void update(Transfer transfer) {
        TransferJpaEntity entity = repository.findById(transfer.id().value())
                .orElseThrow(() -> new TransferNotFoundException(transfer.id()));
        entity.applyChangesFrom(transfer);
    }
}
