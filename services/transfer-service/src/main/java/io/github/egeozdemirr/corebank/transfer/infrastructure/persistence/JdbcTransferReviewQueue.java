package io.github.egeozdemirr.corebank.transfer.infrastructure.persistence;

import io.github.egeozdemirr.corebank.transfer.application.ReviewReason;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReviewQueue;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferReviewQueue implements TransferReviewQueue {

    private static final String FLAG = """
            INSERT INTO transfer_review (transfer_id, reason, detected_at)
            VALUES (:transferId, :reason, :detectedAt)
            ON CONFLICT (transfer_id, reason) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    JdbcTransferReviewQueue(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void flag(TransferId transferId, ReviewReason reason, Instant detectedAt) {
        jdbcClient.sql(FLAG)
                .param("transferId", transferId.value())
                .param("reason", reason.name())
                .param("detectedAt", Timestamp.from(detectedAt))
                .update();
    }
}
