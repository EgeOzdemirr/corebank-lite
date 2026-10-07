package io.github.egeozdemirr.corebank.transfer.application;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import java.time.Instant;
import java.util.Objects;

/**
 * What a posting attempt in account-service led to, classified by the rule of ADR-0004. Only {@link Rejected} may
 * fail a transfer; {@link Unknown} and {@link IdConflict} leave it APPROVED.
 */
public sealed interface PostingOutcome {

    /** Posted now or by an earlier attempt with the same posting id; {@code postedAt} is account-service's time. */
    record Posted(Instant postedAt) implements PostingOutcome {

        public Posted {
            Objects.requireNonNull(postedAt, "postedAt");
        }
    }

    /** A definite business rejection: the money did not move and never will under this posting id. */
    record Rejected(FailureReason reason) implements PostingOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** No answer, an internal error or retries out of time: the posting may or may not exist. */
    record Unknown(String cause) implements PostingOutcome {

        public Unknown {
            Objects.requireNonNull(cause, "cause");
        }
    }

    /** The posting id is taken by a different posting: a bug, never retried, flagged for review. */
    record IdConflict() implements PostingOutcome {
    }
}
