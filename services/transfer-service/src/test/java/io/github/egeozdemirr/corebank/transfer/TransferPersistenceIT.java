package io.github.egeozdemirr.corebank.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TARGET_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.egeozdemirr.corebank.transfer.application.ReviewReason;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferLocker;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReader;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferReviewQueue;
import io.github.egeozdemirr.corebank.transfer.application.port.TransferWriter;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.support.PostgresContainerConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The transfer table round-trips the aggregate and enforces the core rules on its own. */
@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class TransferPersistenceIT {

    private static final LocalDate BUSINESS_DAY = ISTANBUL.businessDayOf(NOW);

    @Autowired
    private TransferWriter transferWriter;

    @Autowired
    private TransferReader transferReader;

    @Autowired
    private TransferLocker transferLocker;

    @Autowired
    private TransferReviewQueue reviewQueue;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void transfer_roundTripsThroughEveryStep() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        transactionTemplate.executeWithoutResult(status -> transferWriter.add(transfer, BUSINESS_DAY));

        transactionTemplate.executeWithoutResult(status -> {
            Transfer locked = transferLocker.lockById(transfer.id()).orElseThrow();
            locked.approve(CHECKER, ISTANBUL, NOW.plusSeconds(60));
            locked.markPosted(NOW.plusSeconds(61));
            transferWriter.update(locked);
        });

        Transfer stored = transferReader.findById(transfer.id()).orElseThrow();
        assertThat(stored.snapshot().checker()).isEqualTo(CHECKER);
        assertThat(stored.snapshot().timeline().postedAt()).contains(NOW.plusSeconds(61));
        assertThat(stored.order()).isEqualTo(transfer.order());
        assertThat(jdbcClient.sql("SELECT business_day FROM transfer WHERE id = :id").param("id", transfer.id().value())
                .query(LocalDate.class).single()).isEqualTo(BUSINESS_DAY);
    }

    @Test
    void failedTransfer_keepsItsReason() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW);
        transfer.markFailed(new FailureReason("INSUFFICIENT_FUNDS"), NOW.plusSeconds(1));
        transactionTemplate.executeWithoutResult(status -> transferWriter.add(transfer, BUSINESS_DAY));

        assertThat(transferReader.findById(transfer.id()).orElseThrow().snapshot().failureReason())
                .isEqualTo(new FailureReason("INSUFFICIENT_FUNDS"));
    }

    @Test
    void unknownTransfer_isEmpty() {
        assertThat(transferReader.findById(TransferId.newId())).isEmpty();
    }

    @Test
    void database_refusesWhatTheDomainForbids() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        transactionTemplate.executeWithoutResult(status -> transferWriter.add(transfer, BUSINESS_DAY));
        String id = transfer.id().toString();

        assertThatThrownBy(() -> sql("UPDATE transfer SET checker_user_id = maker_user_id WHERE id = '" + id + "'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("transfer_checker_is_not_maker_ck");
        assertThatThrownBy(() -> sql("UPDATE transfer SET status = 'CREATED' WHERE id = '" + id + "'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("transfer_status_check");
        assertThatThrownBy(() -> sql("UPDATE transfer SET status = 'FAILED' WHERE id = '" + id + "'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("transfer_failure_code_iff_failed_ck");
        assertThatThrownBy(() -> sql("UPDATE transfer SET target_account_id = source_account_id WHERE id = '"
                + id + "'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("transfer_distinct_accounts_ck");
    }

    /**
     * PostgreSQL puts the whole failing row into a constraint error ("Failing row contains ..."), and that text would
     * end up in the logged stack trace. The transfer row holds IBANs and the beneficiary name.
     */
    @Test
    void constraintViolation_doesNotCarryTheRowIntoTheException() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("75000.00"), POLICY, NOW);
        transactionTemplate.executeWithoutResult(status -> transferWriter.add(transfer, BUSINESS_DAY));

        assertThatThrownBy(() -> sql("UPDATE transfer SET checker_user_id = maker_user_id WHERE id = '"
                + transfer.id() + "'"))
                .satisfies(violation -> assertThat(fullText(violation))
                        .contains("transfer_checker_is_not_maker_ck")
                        .doesNotContain(SOURCE_IBAN, TARGET_IBAN, "Mehmet Demir"));
    }

    @Test
    void reviewQueue_keepsOneEntryPerTransferAndReason() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW);
        transactionTemplate.executeWithoutResult(status -> transferWriter.add(transfer, BUSINESS_DAY));

        transactionTemplate.executeWithoutResult(status -> {
            reviewQueue.flag(transfer.id(), ReviewReason.POSTING_ID_CONFLICT, NOW);
            reviewQueue.flag(transfer.id(), ReviewReason.POSTING_ID_CONFLICT, NOW.plusSeconds(5));
        });

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM transfer_review WHERE transfer_id = :id")
                .param("id", transfer.id().value()).query(Long.class).single()).isOne();
    }

    private static String fullText(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            text.append(cause).append('\n');
        }
        return text.toString();
    }

    private void sql(String statement) {
        jdbcClient.sql(statement).update();
    }
}
