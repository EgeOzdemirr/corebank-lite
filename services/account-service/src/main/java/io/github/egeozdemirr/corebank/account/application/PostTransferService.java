package io.github.egeozdemirr.corebank.account.application;

import io.github.egeozdemirr.corebank.account.application.port.LedgerReader;
import io.github.egeozdemirr.corebank.account.domain.exception.PostingConflictException;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a transfer between two customer accounts on behalf of transfer-service. Idempotent on the posting id, so a
 * caller that did not learn the outcome (timeout, lost connection) can repeat the request with the same id.
 */
@Service
public class PostTransferService {

    private final LedgerPostingService ledgerPostingService;
    private final LedgerReader ledgerReader;
    private final Clock clock;

    public PostTransferService(LedgerPostingService ledgerPostingService, LedgerReader ledgerReader, Clock clock) {
        this.ledgerPostingService = ledgerPostingService;
        this.ledgerReader = ledgerReader;
        this.clock = clock;
    }

    /**
     * A repeated request gets the original result; a request that reuses the id for a different movement is
     * rejected and changes nothing.
     */
    @Transactional
    public PostTransferResult post(PostTransferCommand command) {
        Posting requested = Posting.between(command.postingId(), PostingType.TRANSFER, command.debitAccount(),
                command.creditAccount(), command.amount(), clock.instant());
        if (ledgerPostingService.post(requested) == PostingOutcome.POSTED) {
            return new PostTransferResult(requested.id(), requested.postedAt(), false);
        }
        Posting recorded = ledgerReader.findPosting(requested.id())
                .orElseThrow(() -> new IllegalStateException("Posting " + requested.id() + " claimed but not found"));
        if (!recorded.describesSameMovementAs(requested)) {
            throw new PostingConflictException(requested.id());
        }
        return new PostTransferResult(recorded.id(), recorded.postedAt(), true);
    }
}
