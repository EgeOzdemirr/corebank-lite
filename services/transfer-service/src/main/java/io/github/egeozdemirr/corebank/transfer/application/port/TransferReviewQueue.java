package io.github.egeozdemirr.corebank.transfer.application.port;

import io.github.egeozdemirr.corebank.transfer.application.ReviewReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.time.Instant;

/**
 * Transfers that need a human: their state is not wrong, but something about them points to a bug. Automatic
 * recovery (roadmap week 3) must skip every transfer listed here.
 */
public interface TransferReviewQueue {

    /** Adding the same transfer for the same reason twice keeps one entry. */
    void flag(TransferId transferId, ReviewReason reason, Instant detectedAt);
}
