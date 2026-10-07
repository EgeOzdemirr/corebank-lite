package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from TransferJpaEntity transfer where transfer.id = :id")
    Optional<TransferJpaEntity> findAndLockById(@Param("id") UUID id);
}
