package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransferValuesTest {

    @Test
    void failureReason_isAStableUpperCaseCode() {
        assertThat(new FailureReason("INSUFFICIENT_FUNDS")).hasToString("INSUFFICIENT_FUNDS");
    }

    /** Free text could carry a name or an IBAN into the TransferFailed event. */
    @ParameterizedTest
    @ValueSource(strings = {"", "insufficient_funds", "Insufficient funds on TR80 9999", "1_FAILED"})
    void failureReason_rejectsFreeText(String candidate) {
        assertThatThrownBy(() -> new FailureReason(candidate)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeline_recordsEachStepOnceAndComparesByValue() {
        TransferTimeline requested = TransferTimeline.requestedAt(NOW);
        TransferTimeline complete = requested.approved(NOW.plusSeconds(1)).posted(NOW.plusSeconds(2))
                .reversed(NOW.plusSeconds(4));

        assertThat(requested.approvedAt()).isEmpty();
        assertThat(complete.requestedAt()).isEqualTo(NOW);
        assertThat(complete.postedAt()).contains(NOW.plusSeconds(2));
        assertThat(complete).isEqualTo(TransferTimeline.restore(NOW, NOW.plusSeconds(1), NOW.plusSeconds(2), null,
                NOW.plusSeconds(4))).hasSameHashCodeAs(TransferTimeline.restore(NOW, NOW.plusSeconds(1),
                NOW.plusSeconds(2), null, NOW.plusSeconds(4)));
        assertThat(complete).isNotEqualTo(requested).hasToString(complete.toString());
        assertThat(complete.toString()).contains("requestedAt=" + NOW);
    }

    @Test
    void transferId_isTheUuid() {
        UUID value = UUID.randomUUID();

        assertThat(new TransferId(value)).hasToString(value.toString());
        assertThat(TransferId.newId()).isNotEqualTo(TransferId.newId());
    }
}
