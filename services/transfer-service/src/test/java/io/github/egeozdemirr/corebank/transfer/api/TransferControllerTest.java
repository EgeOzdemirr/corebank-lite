package io.github.egeozdemirr.corebank.transfer.api;

import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.CHECKER;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.ISTANBUL;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.NOW;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.POLICY;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.money;
import static io.github.egeozdemirr.corebank.transfer.support.TestTransfers.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.egeozdemirr.corebank.transfer.application.AccountServiceUnavailableException;
import io.github.egeozdemirr.corebank.transfer.application.ApproveTransferService;
import io.github.egeozdemirr.corebank.transfer.application.CreateTransferService;
import io.github.egeozdemirr.corebank.transfer.application.RejectTransferService;
import io.github.egeozdemirr.corebank.transfer.application.TransferQueryService;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.ApprovalExpiredException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.DailyLimitExceededException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.DomainException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.InvalidStateTransitionException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.MakerCannotApproveException;
import io.github.egeozdemirr.corebank.transfer.domain.exception.TransferNotFoundException;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String TRANSFERS = TransferController.BASE_PATH;
    private static final String VALID_BODY = """
            {"sourceAccountId": "%s", "targetIban": "TR53 9999 9000 0000 0000 0000 02",
             "beneficiaryName": "Mehmet Demir", "amount": {"amount": "%s", "currency": "TRY"},
             "channel": "MOBILE"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CreateTransferService createTransferService;

    @MockitoBean
    private ApproveTransferService approveTransferService;

    @MockitoBean
    private RejectTransferService rejectTransferService;

    @MockitoBean
    private TransferQueryService transferQueryService;

    @Test
    void postedTransfer_is201WithLocation() {
        Transfer posted = Transfer.request(TransferId.newId(), order("100.00"), POLICY, NOW);
        posted.markPosted(NOW.plusSeconds(1));
        given(createTransferService.create(any())).willReturn(posted);

        MvcTestResult result = create("maker-1", body("100.00"));

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", TRANSFERS + "/" + posted.id());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("POSTED");
        assertThat(result).bodyJson().extractingPath("$.checkerUserId").isNull();
        assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo("100.00");
    }

    /** 201 means "recorded and finished", not "succeeded": the client must read status and failureCode. */
    @Test
    void definitelyRejectedTransfer_is201WithStatusFailed() {
        Transfer failed = Transfer.request(TransferId.newId(), order("100.00"), POLICY, NOW);
        failed.markFailed(new FailureReason("INSUFFICIENT_FUNDS"), NOW.plusSeconds(1));
        given(createTransferService.create(any())).willReturn(failed);

        MvcTestResult result = create("maker-1", body("100.00"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("FAILED");
        assertThat(result).bodyJson().extractingPath("$.failureCode").isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void pendingOrStillApprovedTransfer_is202WithLocation() {
        Transfer pending = Transfer.request(TransferId.newId(), order("60000.00"), POLICY, NOW);
        Transfer approvedUnknown = Transfer.request(TransferId.newId(), order("100.00"), POLICY, NOW);
        given(createTransferService.create(any())).willReturn(pending, approvedUnknown);

        assertThat(create("maker-1", body("60000.00"))).hasStatus(HttpStatus.ACCEPTED)
                .hasHeader("Location", TRANSFERS + "/" + pending.id())
                .bodyJson().extractingPath("$.status").isEqualTo("PENDING_APPROVAL");
        assertThat(create("maker-1", body("100.00"))).hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    void missingActorHeader_isBadRequestAndNothingHappens() {
        MvcTestResult result = mvc.post().uri(TRANSFERS).contentType(MediaType.APPLICATION_JSON)
                .content(body("100.00")).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(createTransferService);
    }

    @Test
    void unsafeActorHeader_isInvalidUserId() {
        assertThat(create("maker 1", body("100.00"))).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_USER_ID");
        verifyNoInteractions(createTransferService);
    }

    @Test
    void malformedBody_isBadRequest() {
        assertThat(create("maker-1", body("100.005"))).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_REQUEST");
        assertThat(create("maker-1", body("100.00").replace("MOBILE", "CARRIER_PIGEON")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(create("maker-1", body("100.00").replace("TR53 9999", "TR00 9999")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_IBAN");
        verifyNoInteractions(createTransferService);
    }

    static Stream<Arguments> rejections() {
        TransferId transferId = TransferId.newId();
        return Stream.of(
                Arguments.of(new AccountNotFoundException(new AccountId(UUID.randomUUID())), HttpStatus.NOT_FOUND),
                Arguments.of(new DailyLimitExceededException(money("2.00"), money("1.00")),
                        HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(new MakerCannotApproveException(transferId), HttpStatus.UNPROCESSABLE_CONTENT),
                Arguments.of(new ApprovalExpiredException(transferId), HttpStatus.CONFLICT),
                Arguments.of(new InvalidStateTransitionException(transferId, TransferStatus.POSTED,
                        TransferStatus.APPROVED), HttpStatus.CONFLICT),
                Arguments.of(new TransferNotFoundException(transferId), HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("rejections")
    void domainRejection_mapsToItsStatusAndCode(DomainException rejection, HttpStatus status) {
        given(approveTransferService.approve(any())).willThrow(rejection);

        assertThat(approve(UUID.randomUUID())).hasStatus(status)
                .bodyJson().extractingPath("$.errorCode").isEqualTo(rejection.errorCode());
    }

    @Test
    void unreachableAccountService_is503() {
        given(createTransferService.create(any()))
                .willThrow(new AccountServiceUnavailableException("down", null));

        assertThat(create("maker-1", body("100.00"))).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
    }

    @Test
    void concurrentChange_is409AndUnexpectedError_is500WithoutDetails() throws Exception {
        given(approveTransferService.approve(any()))
                .willThrow(new OptimisticLockingFailureException("stale"))
                .willThrow(new IllegalStateException("secret internal detail"));

        assertThat(approve(UUID.randomUUID())).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("CONCURRENT_MODIFICATION");
        MvcTestResult unexpected = approve(UUID.randomUUID());
        assertThat(unexpected).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
        assertThat(unexpected.getResponse().getContentAsString()).doesNotContain("secret internal detail");
    }

    @Test
    void approval_is200WhenFinishedAnd202WhenThePostingIsStillOpen() {
        Transfer posted = decided(TransferStatus.POSTED);
        Transfer stillApproved = decided(TransferStatus.APPROVED);
        given(approveTransferService.approve(any())).willReturn(posted, stillApproved);

        assertThat(approve(posted.id().value())).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.checkerUserId").isEqualTo(CHECKER.value());
        assertThat(approve(stillApproved.id().value())).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void rejection_is200WithTheReason() {
        Transfer rejected = Transfer.request(TransferId.newId(), order("60000.00"), POLICY, NOW);
        rejected.reject(CHECKER, ISTANBUL, NOW.plusSeconds(10));
        given(rejectTransferService.reject(any())).willReturn(rejected);

        assertThat(mvc.post().uri(TRANSFERS + "/{id}/rejection", rejected.id().value())
                .header(TransferController.ACTOR_HEADER, CHECKER.value()).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.failureCode").isEqualTo("REJECTED_BY_CHECKER");
    }

    @Test
    void getTransfer_needsNoActor() {
        Transfer transfer = Transfer.request(TransferId.newId(), order("10.00"), POLICY, NOW);
        given(transferQueryService.getTransfer(transfer.id())).willReturn(transfer);

        assertThat(mvc.get().uri(TRANSFERS + "/{id}", transfer.id().value()).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.source.iban").isEqualTo("TR809999900000000000000001");
    }

    private MvcTestResult create(String actor, String body) {
        return mvc.post().uri(TRANSFERS).header(TransferController.ACTOR_HEADER, actor)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult approve(UUID transferId) {
        return mvc.post().uri(TRANSFERS + "/{id}/approval", transferId)
                .header(TransferController.ACTOR_HEADER, CHECKER.value()).exchange();
    }

    private static String body(String amount) {
        return VALID_BODY.formatted(UUID.randomUUID(), amount);
    }

    private static Transfer decided(TransferStatus target) {
        Transfer transfer = Transfer.request(TransferId.newId(), order("60000.00"), POLICY, NOW);
        transfer.approve(CHECKER, ISTANBUL, NOW.plusSeconds(10));
        if (target == TransferStatus.POSTED) {
            transfer.markPosted(NOW.plusSeconds(11));
        }
        return transfer;
    }
}
