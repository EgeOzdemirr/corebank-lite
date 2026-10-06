package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.EntryDirection;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntryId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.domain.money.Currencies;
import io.github.egeozdemirr.corebank.account.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Ledger lines are append-only; the database also rejects UPDATE and DELETE on this table. */
@Entity
@Immutable
@Table(name = "ledger_entry")
class LedgerEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryDirection direction;

    @Column(nullable = false, precision = 19, scale = Money.SCALE)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_type", nullable = false, length = 32)
    private PostingType postingType;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected LedgerEntryJpaEntity() {
        // required by JPA
    }

    static LedgerEntryJpaEntity from(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = new LedgerEntryJpaEntity();
        entity.id = entry.id().value();
        entity.postingId = entry.postingId().value();
        entity.accountId = entry.accountId().value();
        entity.direction = entry.direction();
        entity.amount = entry.amount().amount();
        entity.currency = entry.amount().currency().getCurrencyCode();
        entity.postingType = entry.postingType();
        entity.postedAt = entry.postedAt();
        return entity;
    }

    LedgerEntry toDomain() {
        return new LedgerEntry(new LedgerEntryId(id), new PostingId(postingId), new AccountId(accountId), direction,
                Money.of(amount, Currencies.fromCode(currency)), postingType, postedAt);
    }
}
