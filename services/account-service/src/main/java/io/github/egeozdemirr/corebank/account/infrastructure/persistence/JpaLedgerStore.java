package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.application.port.LedgerReader;
import io.github.egeozdemirr.corebank.account.application.port.LedgerWriter;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JpaLedgerStore implements LedgerReader, LedgerWriter {

    /**
     * JPA cannot express ON CONFLICT. PostgreSQL makes a second inserter of the same id wait until the first one
     * commits (then nothing is inserted) or rolls back (then this insert goes ahead), so exactly one transaction wins.
     */
    private static final String CLAIM_POSTING = """
            INSERT INTO posting (id, posting_type, posted_at)
            VALUES (:id, :postingType, :postedAt)
            ON CONFLICT (id) DO NOTHING
            """;

    private final LedgerEntryJpaRepository repository;
    private final JdbcClient jdbcClient;

    JpaLedgerStore(LedgerEntryJpaRepository repository, JdbcClient jdbcClient) {
        this.repository = repository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean claim(Posting posting) {
        int inserted = jdbcClient.sql(CLAIM_POSTING)
                .param("id", posting.id().value())
                .param("postingType", posting.type().name())
                .param("postedAt", Timestamp.from(posting.postedAt()))
                .update();
        return inserted == 1;
    }

    @Override
    public void append(Posting posting) {
        repository.saveAll(posting.entries().stream().map(LedgerEntryJpaEntity::from).toList());
    }

    @Override
    public Optional<Posting> findPosting(PostingId postingId) {
        List<LedgerEntry> entries = repository.findByPostingId(postingId.value()).stream()
                .map(LedgerEntryJpaEntity::toDomain)
                .toList();
        return entries.isEmpty() ? Optional.empty() : Optional.of(Posting.of(postingId, entries));
    }

    @Override
    public PageResult<LedgerEntry> findEntries(AccountId accountId, PageQuery pageQuery) {
        Page<LedgerEntryJpaEntity> page = repository.findByAccountIdOrderByPostedAtDescIdDesc(
                accountId.value(), PageRequest.of(pageQuery.page(), pageQuery.size()));
        return new PageResult<>(page.map(LedgerEntryJpaEntity::toDomain).getContent(), pageQuery.page(),
                pageQuery.size(), page.getTotalElements());
    }
}
