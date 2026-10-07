package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import java.time.Instant;
import java.util.Objects;

/** {@code alreadyPosted} is true when an earlier request with the same posting id recorded the posting. */
public record PostTransferResult(PostingId postingId, Instant postedAt, boolean alreadyPosted) {

    public PostTransferResult {
        Objects.requireNonNull(postingId, "postingId");
        Objects.requireNonNull(postedAt, "postedAt");
    }
}
