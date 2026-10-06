package io.github.egeozdemirr.corebank.account.infrastructure.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
}
