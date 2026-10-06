package io.github.egeozdemirr.corebank.account.infrastructure.outbox;

import io.github.egeozdemirr.corebank.contracts.EventCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of the transactional outbox. It is written in the same transaction as the business change; a separate
 * relay (roadmap week 3) publishes unpublished rows to {@link #topic} and sets {@code published_at}.
 */
@Entity
@Table(name = "outbox_event")
class OutboxEventJpaEntity {

    /** Equals the envelope's eventId, so consumers can deduplicate across relay retries. */
    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 64)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventJpaEntity() {
        // required by JPA
    }

    OutboxEventJpaEntity(UUID eventId, EventCatalog definition, String aggregateType, UUID aggregateId,
                         String partitionKey, String payload, Instant occurredAt) {
        this.id = eventId;
        this.eventType = definition.eventType();
        this.schemaVersion = definition.schemaVersion();
        this.topic = definition.topic();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
