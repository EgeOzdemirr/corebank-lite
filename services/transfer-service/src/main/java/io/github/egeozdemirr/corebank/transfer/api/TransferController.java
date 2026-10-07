package io.github.egeozdemirr.corebank.transfer.api;

import io.github.egeozdemirr.corebank.transfer.application.ApproveTransferService;
import io.github.egeozdemirr.corebank.transfer.application.CreateTransferService;
import io.github.egeozdemirr.corebank.transfer.application.RejectTransferService;
import io.github.egeozdemirr.corebank.transfer.application.TransferDecisionCommand;
import io.github.egeozdemirr.corebank.transfer.application.TransferQueryService;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferId;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferStatus;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP translation only: request to command, transfer to response and status code. The acting user comes from
 * {@code X-Actor-User-Id} until authentication arrives (roadmap week 4); write endpoints require it, because
 * maker-checker cannot tell two anonymous users apart.
 */
@RestController
@RequestMapping(TransferController.BASE_PATH)
@Tag(name = "Transfers")
public class TransferController {

    static final String BASE_PATH = "/api/v1/transfers";
    static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final CreateTransferService createTransferService;
    private final ApproveTransferService approveTransferService;
    private final RejectTransferService rejectTransferService;
    private final TransferQueryService transferQueryService;

    public TransferController(CreateTransferService createTransferService,
                              ApproveTransferService approveTransferService,
                              RejectTransferService rejectTransferService,
                              TransferQueryService transferQueryService) {
        this.createTransferService = createTransferService;
        this.approveTransferService = approveTransferService;
        this.rejectTransferService = rejectTransferService;
        this.transferQueryService = transferQueryService;
    }

    @PostMapping
    @Operation(summary = "Request a transfer; up to the approval threshold it is approved and posted at once")
    @ApiResponse(responseCode = "201", description = "Recorded and finished. This does NOT mean success: status is "
            + "POSTED, or FAILED with a failureCode when account-service definitely rejected the posting.")
    @ApiResponse(responseCode = "202", description = "Recorded and not finished: PENDING_APPROVAL (waits for a "
            + "checker) or APPROVED (posting outcome unknown, completed later with the same posting id)")
    @ApiResponse(responseCode = "400", description = "Invalid request; nothing was recorded")
    @ApiResponse(responseCode = "404", description = "Unknown source or target account; nothing was recorded")
    @ApiResponse(responseCode = "422", description = "Limit exceeded or account closed; nothing was recorded")
    @ApiResponse(responseCode = "503", description = "account-service unreachable; nothing was recorded")
    public ResponseEntity<TransferResponse> createTransfer(@RequestHeader(ACTOR_HEADER) String actor,
                                                           @Valid @RequestBody CreateTransferRequest request) {
        Transfer transfer = createTransferService.create(request.toCommand(new UserId(actor)));
        return ResponseEntity.status(finished(transfer) ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .location(URI.create(BASE_PATH + "/" + transfer.id()))
                .body(TransferResponse.from(transfer));
    }

    @PostMapping("/{transferId}/approval")
    @Operation(summary = "Approve a transfer as checker (not its maker); it is posted right after")
    @ApiResponse(responseCode = "200", description = "Finished: POSTED, or FAILED with a failureCode")
    @ApiResponse(responseCode = "202", description = "APPROVED, posting outcome unknown; completed later")
    @ApiResponse(responseCode = "409", description = "Not waiting for approval, or the approval expired "
            + "(APPROVAL_EXPIRED; the transfer is now FAILED)")
    @ApiResponse(responseCode = "422", description = "The maker cannot approve their own transfer")
    public ResponseEntity<TransferResponse> approveTransfer(@RequestHeader(ACTOR_HEADER) String actor,
                                                            @PathVariable UUID transferId) {
        Transfer transfer = approveTransferService.approve(decision(transferId, actor));
        return ResponseEntity.status(finished(transfer) ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(TransferResponse.from(transfer));
    }

    @PostMapping("/{transferId}/rejection")
    @Operation(summary = "Reject a transfer as checker (not its maker): FAILED with REJECTED_BY_CHECKER")
    @ApiResponse(responseCode = "200", description = "FAILED with failureCode REJECTED_BY_CHECKER")
    @ApiResponse(responseCode = "409", description = "Not waiting for approval, or the approval expired")
    @ApiResponse(responseCode = "422", description = "The maker cannot reject their own transfer")
    public TransferResponse rejectTransfer(@RequestHeader(ACTOR_HEADER) String actor,
                                           @PathVariable UUID transferId) {
        return TransferResponse.from(rejectTransferService.reject(decision(transferId, actor)));
    }

    @GetMapping("/{transferId}")
    @Operation(summary = "Get a transfer")
    public TransferResponse getTransfer(@PathVariable UUID transferId) {
        return TransferResponse.from(transferQueryService.getTransfer(new TransferId(transferId)));
    }

    private static TransferDecisionCommand decision(UUID transferId, String actor) {
        return new TransferDecisionCommand(new TransferId(transferId), new UserId(actor));
    }

    /** POSTED and FAILED will not change any more without a new action; the others still will. */
    private static boolean finished(Transfer transfer) {
        return transfer.status() == TransferStatus.POSTED || transfer.status() == TransferStatus.FAILED;
    }
}
