package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerEntriesResponse(List<Entry> entries, int page, int size, long totalEntries) {

    public LedgerEntriesResponse {
        entries = List.copyOf(entries);
    }

    static LedgerEntriesResponse from(PageResult<LedgerEntry> result) {
        return new LedgerEntriesResponse(result.items().stream().map(Entry::from).toList(), result.page(),
                result.size(), result.totalItems());
    }

    public record Entry(
            UUID entryId,
            UUID postingId,
            String postingType,
            String direction,
            MoneyResponse amount,
            Instant postedAt) {

        static Entry from(LedgerEntry entry) {
            return new Entry(entry.id().value(), entry.postingId().value(), entry.postingType().name(),
                    entry.direction().name(), MoneyResponse.from(entry.amount()), entry.postedAt());
        }
    }
}
