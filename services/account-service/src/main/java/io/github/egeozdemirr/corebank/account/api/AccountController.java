package io.github.egeozdemirr.corebank.account.api;

import io.github.egeozdemirr.corebank.account.application.AccountQueryService;
import io.github.egeozdemirr.corebank.account.application.LedgerQueryService;
import io.github.egeozdemirr.corebank.account.application.OpenAccountService;
import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP translation only: request to command, result to response. Business rules live in the service layer. */
@RestController
@RequestMapping(AccountController.BASE_PATH)
@Tag(name = "Accounts")
public class AccountController {

    static final String BASE_PATH = "/api/v1/accounts";
    static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_PAGE_SIZE = "20";

    private final OpenAccountService openAccountService;
    private final AccountQueryService accountQueryService;
    private final LedgerQueryService ledgerQueryService;

    public AccountController(OpenAccountService openAccountService, AccountQueryService accountQueryService,
                             LedgerQueryService ledgerQueryService) {
        this.openAccountService = openAccountService;
        this.accountQueryService = accountQueryService;
        this.ledgerQueryService = ledgerQueryService;
    }

    @PostMapping
    @Operation(summary = "Open a customer account, optionally with an opening deposit")
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        Account account = openAccountService.open(request.toCommand());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + account.id()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get an account")
    public AccountResponse getAccount(@PathVariable UUID accountId) {
        return AccountResponse.from(accountQueryService.getAccount(new AccountId(accountId)));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get the current balance of an account")
    public BalanceResponse getBalance(@PathVariable UUID accountId) {
        return BalanceResponse.from(accountQueryService.getBalance(new AccountId(accountId)));
    }

    @GetMapping("/{accountId}/ledger-entries")
    @Operation(summary = "List ledger entries of an account, newest first")
    public LedgerEntriesResponse getLedgerEntries(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return LedgerEntriesResponse.from(
                ledgerQueryService.getEntries(new AccountId(accountId), new PageQuery(page, size)));
    }
}
