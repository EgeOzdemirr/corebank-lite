package io.github.egeozdemirr.corebank.transfer.application;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.SOURCE_IBAN;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.TARGET_IBAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** The WARN and ERROR lines of the posting step carry identifiers, status and codes only (KVKK). */
@ExtendWith(OutputCaptureExtension.class)
class TransferPostingStepLogTest {

    private final TransferServicesFixture fixture = new TransferServicesFixture();

    @Test
    void unknownOutcome_isLoggedWithIdsStatusAndCauseOnly(CapturedOutput output) {
        fixture.ledger.willAnswer(new PostingOutcome.Unknown("DEADLINE_EXCEEDED"));

        Transfer transfer = fixture.createService.create(fixture.command("100.00"));

        assertThat(output.getOut()).contains("WARN", "transferId=" + transfer.id(), "postingId=" + transfer.id(),
                "status=APPROVED", "cause=DEADLINE_EXCEEDED");
        assertNoPersonalData(output);
    }

    @Test
    void postingIdConflict_isLoggedWithIdsStatusAndErrorCodeOnly(CapturedOutput output) {
        fixture.ledger.willAnswer(new PostingOutcome.IdConflict());

        Transfer transfer = fixture.createService.create(fixture.command("100.00"));

        assertThat(output.getOut()).contains("ERROR", "transferId=" + transfer.id(), "postingId=" + transfer.id(),
                "status=APPROVED", "errorCode=POSTING_ID_CONFLICT");
        assertNoPersonalData(output);
    }

    private static void assertNoPersonalData(CapturedOutput output) {
        assertThat(output.getOut()).doesNotContain(SOURCE_IBAN, TARGET_IBAN, "Mehmet Demir", "100.00 TRY");
    }
}
