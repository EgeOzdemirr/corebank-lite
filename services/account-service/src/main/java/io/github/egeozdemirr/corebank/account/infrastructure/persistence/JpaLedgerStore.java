package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.application.port.LedgerReader;
import io.github.egeozdemirr.corebank.account.application.port.LedgerWriter;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaLedgerStore implements LedgerReader, LedgerWriter {

    private final LedgerEntryJpaRepository repository;

    JpaLedgerStore(LedgerEntryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(Posting posting) {
        repository.saveAll(posting.entries().stream().map(LedgerEntryJpaEntity::from).toList());
    }

    @Override
    public PageResult<LedgerEntry> findEntries(AccountId accountId, PageQuery pageQuery) {
        Page<LedgerEntryJpaEntity> page = repository.findByAccountIdOrderByPostedAtDescIdDesc(
                accountId.value(), PageRequest.of(pageQuery.page(), pageQuery.size()));
        return new PageResult<>(page.map(LedgerEntryJpaEntity::toDomain).getContent(), pageQuery.page(),
                pageQuery.size(), page.getTotalElements());
    }
}
