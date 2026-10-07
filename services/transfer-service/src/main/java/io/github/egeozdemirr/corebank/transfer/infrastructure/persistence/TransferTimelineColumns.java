package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferTimeline;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
class TransferTimelineColumns {

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected TransferTimelineColumns() {
        // required by JPA
    }

    static TransferTimelineColumns from(TransferTimeline timeline) {
        TransferTimelineColumns columns = new TransferTimelineColumns();
        columns.requestedAt = timeline.requestedAt();
        columns.approvedAt = timeline.approvedAt().orElse(null);
        columns.postedAt = timeline.postedAt().orElse(null);
        columns.failedAt = timeline.failedAt().orElse(null);
        columns.reversedAt = timeline.reversedAt().orElse(null);
        return columns;
    }

    TransferTimeline toDomain() {
        return TransferTimeline.restore(requestedAt, approvedAt, postedAt, failedAt, reversedAt);
    }
}
