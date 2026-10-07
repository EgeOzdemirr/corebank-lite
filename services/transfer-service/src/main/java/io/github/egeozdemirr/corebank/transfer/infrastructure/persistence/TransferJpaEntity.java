package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferSnapshot;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Mirrors the aggregate: the immutable order, the changing decision state and the timeline. */
@Entity
@Table(name = "transfer")
class TransferJpaEntity {

    @Id
    private UUID id;

    @Embedded
    private TransferOrderColumns order;

    @Column(name = "approval_required", nullable = false, updatable = false)
    private boolean approvalRequired;

    @Column(name = "business_day", nullable = false, updatable = false)
    private LocalDate businessDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "checker_user_id", length = 64)
    private String checkerUserId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Embedded
    private TransferTimelineColumns timeline;

    /** Optimistic lock: a second writer that did not take the row lock fails instead of overwriting a decision. */
    @Version
    private Long version;

    protected TransferJpaEntity() {
        // required by JPA
    }

    static TransferJpaEntity newFrom(Transfer transfer, LocalDate businessDay) {
        TransferSnapshot state = transfer.snapshot();
        TransferJpaEntity entity = new TransferJpaEntity();
        entity.id = state.id().value();
        entity.order = TransferOrderColumns.from(state.order());
        entity.approvalRequired = state.approvalRequired();
        entity.businessDay = businessDay;
        entity.applyChangesFrom(transfer);
        return entity;
    }

    /** Copies what a transfer may change after it was requested; the order itself is immutable. */
    void applyChangesFrom(Transfer transfer) {
        TransferSnapshot state = transfer.snapshot();
        status = state.status();
        checkerUserId = Optional.ofNullable(state.checker()).map(UserId::value).orElse(null);
        failureCode = Optional.ofNullable(state.failureReason()).map(FailureReason::code).orElse(null);
        timeline = TransferTimelineColumns.from(state.timeline());
    }

    Transfer toDomain() {
        return Transfer.restore(new TransferSnapshot(new TransferId(id), order.toDomain(), approvalRequired, status,
                Optional.ofNullable(checkerUserId).map(UserId::new).orElse(null), timeline.toDomain(),
                Optional.ofNullable(failureCode).map(FailureReason::new).orElse(null)));
    }
}
