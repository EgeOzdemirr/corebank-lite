package io.github.egeozdemirr.corebank.transfer.api;

import io.github.egeozdemirr.corebank.transfer.domain.account.AccountReference;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.FailureReason;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.Transfer;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferOrder;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferSnapshot;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferTimeline;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Schema(description = "A transfer as recorded. Always read status: a transfer can be recorded (HTTP 201) and still be "
        + "FAILED, in which case failureCode says why.")
public record TransferResponse(
        UUID transferId,
        @Schema(description = "PENDING_APPROVAL, APPROVED, POSTED, FAILED or REVERSED. APPROVED after a request means "
                + "the posting outcome is not known yet; the transfer will be completed later.")
        String status,
        boolean approvalRequired,
        AccountResponse source,
        AccountResponse target,
        String beneficiaryName,
        MoneyResponse amount,
        String channel,
        String makerUserId,
        @Schema(description = "User who approved or rejected the transfer; null when nobody decided")
        String checkerUserId,
        @Schema(description = "Stable reason when status is FAILED, for example INSUFFICIENT_FUNDS, "
                + "REJECTED_BY_CHECKER or APPROVAL_EXPIRED")
        String failureCode,
        Instant requestedAt,
        Instant approvedAt,
        Instant postedAt,
        Instant failedAt) {

    static TransferResponse from(Transfer transfer) {
        TransferSnapshot state = transfer.snapshot();
        TransferOrder order = state.order();
        TransferTimeline timeline = state.timeline();
        return new TransferResponse(state.id().value(), state.status().name(), state.approvalRequired(),
                AccountResponse.from(order.source()), AccountResponse.from(order.target()),
                order.beneficiaryName().value(), MoneyResponse.from(order.amount()), order.channel().name(),
                order.maker().value(), Optional.ofNullable(state.checker()).map(UserId::value).orElse(null),
                Optional.ofNullable(state.failureReason()).map(FailureReason::code).orElse(null),
                timeline.requestedAt(), timeline.approvedAt().orElse(null), timeline.postedAt().orElse(null),
                timeline.failedAt().orElse(null));
    }

    public record AccountResponse(UUID accountId, String iban) {

        static AccountResponse from(AccountReference account) {
            return new AccountResponse(account.accountId().value(), account.iban().value());
        }
    }
}
