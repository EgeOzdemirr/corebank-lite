package io.github.egeozdemirr.corebank.account.support;

import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.application.port.LedgerReader;
import io.github.egeozdemirr.corebank.account.application.port.LedgerWriter;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class InMemoryLedger implements LedgerReader, LedgerWriter {

    private final List<Posting> postings = new ArrayList<>();
    private final Set<PostingId> claimedIds = new HashSet<>();

    @Override
    public boolean claim(Posting posting) {
        return claimedIds.add(posting.id());
    }

    @Override
    public void append(Posting posting) {
        postings.add(posting);
    }

    @Override
    public Optional<Posting> findPosting(PostingId postingId) {
        return postings.stream().filter(posting -> posting.id().equals(postingId)).findFirst();
    }

    @Override
    public PageResult<LedgerEntry> findEntries(AccountId accountId, PageQuery pageQuery) {
        List<LedgerEntry> entries = postings.stream()
                .flatMap(posting -> posting.entries().stream())
                .filter(entry -> entry.accountId().equals(accountId))
                .sorted(Comparator.comparing(LedgerEntry::postedAt).reversed())
                .toList();
        List<LedgerEntry> page = entries.stream()
                .skip((long) pageQuery.page() * pageQuery.size())
                .limit(pageQuery.size())
                .toList();
        return new PageResult<>(page, pageQuery.page(), pageQuery.size(), entries.size());
    }

    public List<Posting> postings() {
        return List.copyOf(postings);
    }

    /** Signed sum of an account's lines: what the real store derives for accounts without a stored balance. */
    public Money balanceOf(AccountId accountId, Currency currency) {
        return postings.stream()
                .flatMap(posting -> posting.entries().stream())
                .filter(entry -> entry.accountId().equals(accountId))
                .map(LedgerEntry::signedAmount)
                .reduce(Money.zero(currency), Money::plus);
    }
}
