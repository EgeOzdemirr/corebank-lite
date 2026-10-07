package io.github.egeozdemirr.corebank.account.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    Page<LedgerEntryJpaEntity> findByAccountIdOrderByPostedAtDescIdDesc(UUID accountId, Pageable pageable);

    List<LedgerEntryJpaEntity> findByPostingId(UUID postingId);
}
