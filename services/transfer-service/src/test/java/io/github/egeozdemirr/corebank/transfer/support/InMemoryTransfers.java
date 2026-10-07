package io.github.egeozdemirr.corebank.transfer.support;

import io.github.egeozdemirr.corebank.transfer.application.ReviewReason;
import io.github.egeozdemirr.corebank.transfer.application.port.DailyUsageLedger;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferEventPublisher;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferLocker;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReader;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReviewQueue;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferWriter;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.money.Money;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferEvent;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of every persistence port, with the same contracts as the real adapters: stored transfers are copies (changes
 * count only after {@link #update}), the daily total never exceeds its limit and a release goes to the reservation's
 * day. Tests read the recorded state back through the accessors.
 */
public final class InMemoryTransfers implements TransferReader, TransferWriter, TransferLocker, DailyUsageLedger,
        TransferEventPublisher, TransferReviewQueue {

    private final Map<TransferId, Transfer> transfers = new HashMap<>();
    private final Map<TransferId, LocalDate> businessDays = new HashMap<>();
    private final Map<String, Money> dailyTotals = new HashMap<>();
    private final List<TransferEvent> publishedEvents = new ArrayList<>();
    private final Set<TransferId> flaggedForReview = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<Transfer> findById(TransferId transferId) {
        return Optional.ofNullable(transfers.get(transferId)).map(InMemoryTransfers::copy);
    }

    @Override
    public Optional<Transfer> lockById(TransferId transferId) {
        return findById(transferId);
    }

    @Override
    public void add(Transfer transfer, LocalDate businessDay) {
        transfers.put(transfer.id(), copy(transfer));
        businessDays.put(transfer.id(), businessDay);
    }

    @Override
    public void update(Transfer transfer) {
        transfers.put(transfer.id(), copy(transfer));
    }

    @Override
    public boolean reserve(AccountId sourceAccount, LocalDate businessDay, Money amount, Money dailyLimit) {
        String key = key(sourceAccount, businessDay);
        Money total = dailyTotals.getOrDefault(key, Money.zero(amount.currency())).plus(amount);
        if (total.isGreaterThan(dailyLimit)) {
            return false;
        }
        dailyTotals.put(key, total);
        return true;
    }

    @Override
    public void release(TransferId transferId) {
        Transfer transfer = transfers.get(transferId);
        String key = key(transfer.order().source().accountId(), businessDays.get(transferId));
        dailyTotals.put(key, Money.of(dailyTotals.get(key).amount().subtract(transfer.order().amount().amount()),
                transfer.order().amount().currency()));
    }

    @Override
    public void publish(TransferEvent event) {
        publishedEvents.add(event);
    }

    @Override
    public void flag(TransferId transferId, ReviewReason reason, Instant detectedAt) {
        flaggedForReview.add(transferId);
    }

    public Transfer stored(TransferId transferId) {
        return copy(transfers.get(transferId));
    }

    public int storedCount() {
        return transfers.size();
    }

    public Money dailyTotal(AccountId sourceAccount, LocalDate businessDay, Money zero) {
        return dailyTotals.getOrDefault(key(sourceAccount, businessDay), zero);
    }

    public List<TransferEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    public boolean isFlaggedForReview(TransferId transferId) {
        return flaggedForReview.contains(transferId);
    }

    private static String key(AccountId account, LocalDate day) {
        return account + "/" + day;
    }

    private static Transfer copy(Transfer transfer) {
        return Transfer.restore(transfer.snapshot());
    }
}
