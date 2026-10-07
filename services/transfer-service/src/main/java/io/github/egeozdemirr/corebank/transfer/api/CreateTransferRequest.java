package io.github.egeozdemirr.corebank.transfer.api;

import io.github.egeozdemirr.corebank.transfer.application.CreateTransferCommand;
import io.github.egeozdemirr.corebank.transfer.domain.account.AccountId;
import io.github.egeozdemirr.corebank.transfer.domain.identity.Iban;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.BeneficiaryName;
import io.github.egeozdemirr.corebank.transfer.domain.transfer.TransferChannel;
import io.github.egeozdemirr.corebank.transfer.domain.user.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull
        @Schema(description = "Customer account to debit")
        UUID sourceAccountId,

        @NotBlank
        @Schema(description = "IBAN of the customer account to credit; spaces are allowed",
                example = "TR53 9999 9000 0000 0000 0000 02")
        String targetIban,

        @NotBlank
        @Size(max = BeneficiaryName.MAX_LENGTH)
        @Schema(example = "Mehmet Demir")
        String beneficiaryName,

        @NotNull
        @Valid
        MonetaryAmountRequest amount,

        @NotNull
        TransferChannel channel) {

    CreateTransferCommand toCommand(UserId maker) {
        return new CreateTransferCommand(new AccountId(sourceAccountId), Iban.parse(targetIban),
                new BeneficiaryName(beneficiaryName), amount.toMoney(), channel, maker);
    }
}
