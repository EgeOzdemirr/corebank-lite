package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** When each step of a transfer happened. Steps that did not happen (yet) are empty. */
public final class TransferTimeline {

    private final Instant requestedAt;
    private final Instant approvedAt;
    private final Instant postedAt;
    private final Instant failedAt;
    private final Instant reversedAt;

    private TransferTimeline(Instant requestedAt, Instant approvedAt, Instant postedAt, Instant failedAt,
                             Instant reversedAt) {
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.approvedAt = approvedAt;
        this.postedAt = postedAt;
        this.failedAt = failedAt;
        this.reversedAt = reversedAt;
    }

    public static TransferTimeline requestedAt(Instant requestedAt) {
        return new TransferTimeline(requestedAt, null, null, null, null);
    }

    /** Rebuilds a stored timeline; empty steps are passed as null. */
    public static TransferTimeline restore(Instant requestedAt, Instant approvedAt, Instant postedAt, Instant failedAt,
                                           Instant reversedAt) {
        return new TransferTimeline(requestedAt, approvedAt, postedAt, failedAt, reversedAt);
    }

    TransferTimeline approved(Instant at) {
        return new TransferTimeline(requestedAt, Objects.requireNonNull(at, "at"), postedAt, failedAt, reversedAt);
    }

    TransferTimeline posted(Instant at) {
        return new TransferTimeline(requestedAt, approvedAt, Objects.requireNonNull(at, "at"), failedAt, reversedAt);
    }

    TransferTimeline failed(Instant at) {
        return new TransferTimeline(requestedAt, approvedAt, postedAt, Objects.requireNonNull(at, "at"), reversedAt);
    }

    TransferTimeline reversed(Instant at) {
        return new TransferTimeline(requestedAt, approvedAt, postedAt, failedAt, Objects.requireNonNull(at, "at"));
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Optional<Instant> approvedAt() {
        return Optional.ofNullable(approvedAt);
    }

    public Optional<Instant> postedAt() {
        return Optional.ofNullable(postedAt);
    }

    public Optional<Instant> failedAt() {
        return Optional.ofNullable(failedAt);
    }

    public Optional<Instant> reversedAt() {
        return Optional.ofNullable(reversedAt);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TransferTimeline timeline
                && requestedAt.equals(timeline.requestedAt)
                && Objects.equals(approvedAt, timeline.approvedAt)
                && Objects.equals(postedAt, timeline.postedAt)
                && Objects.equals(failedAt, timeline.failedAt)
                && Objects.equals(reversedAt, timeline.reversedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestedAt, approvedAt, postedAt, failedAt, reversedAt);
    }

    @Override
    public String toString() {
        return "TransferTimeline[requestedAt=" + requestedAt + ", approvedAt=" + approvedAt + ", postedAt=" + postedAt
                + ", failedAt=" + failedAt + ", reversedAt=" + reversedAt + "]";
    }
}
