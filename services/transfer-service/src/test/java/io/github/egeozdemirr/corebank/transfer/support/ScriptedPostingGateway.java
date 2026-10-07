package io.github.egeozdemirr.corebank.transfer.support;

import io.github.egeozdemirr.corebank.transfer.application.PostingOutcome;
import io.github.egeozdemirr.corebank.transfer.application.port.LedgerPostingGateway;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Answers postings from a script; once the script is empty every posting succeeds at {@link #POSTED_AT}. */
public final class ScriptedPostingGateway implements LedgerPostingGateway {

    public static final Instant POSTED_AT = Instant.parse("2026-10-07T09:30:05Z");

    private final Deque<PostingOutcome> script = new ArrayDeque<>();
    private final List<TransferId> postedTransfers = new ArrayList<>();

    public void willAnswer(PostingOutcome outcome) {
        script.add(outcome);
    }

    public List<TransferId> calls() {
        return List.copyOf(postedTransfers);
    }

    @Override
    public PostingOutcome post(TransferId transferId, TransferOrder order) {
        postedTransfers.add(transferId);
        return script.isEmpty() ? new PostingOutcome.Posted(POSTED_AT) : script.poll();
    }
}
