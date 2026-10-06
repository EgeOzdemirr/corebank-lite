package io.github.egeozdemirr.corebank.account.api;

import static io.github.egeozdemirr.corebank.account.support.TestAccounts.OPENED_AT;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.TRY;
import static io.github.egeozdemirr.corebank.account.support.TestAccounts.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.github.egeozdemirr.corebank.account.application.AccountBalance;
import io.github.egeozdemirr.corebank.account.application.AccountQueryService;
import io.github.egeozdemirr.corebank.account.application.LedgerQueryService;
import io.github.egeozdemirr.corebank.account.application.OpenAccountCommand;
import io.github.egeozdemirr.corebank.account.application.OpenAccountService;
import io.github.egeozdemirr.corebank.account.application.PageQuery;
import io.github.egeozdemirr.corebank.account.application.PageResult;
import io.github.egeozdemirr.corebank.account.domain.account.Account;
import io.github.egeozdemirr.corebank.account.domain.account.AccountId;
import io.github.egeozdemirr.corebank.account.domain.exception.AccountNotFoundException;
import io.github.egeozdemirr.corebank.account.domain.exception.InsufficientFundsException;
import io.github.egeozdemirr.corebank.account.domain.ledger.LedgerEntry;
import io.github.egeozdemirr.corebank.account.domain.ledger.Posting;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingId;
import io.github.egeozdemirr.corebank.account.domain.ledger.PostingType;
import io.github.egeozdemirr.corebank.account.support.SyntheticData;
import io.github.egeozdemirr.corebank.account.support.TestAccounts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OpenAccountService openAccountService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private LedgerQueryService ledgerQueryService;

    @Test
    void openAccount_returnsCreatedWithLocationAndAccount() {
        Account account = TestAccounts.customerAccount("1500.00");
        given(openAccountService.open(any())).willReturn(account);

        MvcTestResult result = postAccount(requestJson(SyntheticData.tckn(), "TRY", "1500.00"));

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", ACCOUNTS + "/" + account.id());
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("1500.00");
        assertThat(result).bodyJson().extractingPath("$.accountType").isEqualTo("CUSTOMER");
        assertThat(result).bodyJson().extractingPath("$.iban").asString().startsWith("TR");
        assertThat(result).bodyText().doesNotContain("holderTckn");
    }

    @Test
    void openAccount_translatesRequestIntoCommand() {
        given(openAccountService.open(any())).willReturn(TestAccounts.customerAccount("0.00"));
        String tckn = SyntheticData.tckn();

        postAccount(requestJson(tckn, "TRY", null));

        ArgumentCaptor<OpenAccountCommand> command = ArgumentCaptor.forClass(OpenAccountCommand.class);
        Mockito.verify(openAccountService).open(command.capture());
        assertThat(command.getValue().tckn().value()).isEqualTo(tckn);
        assertThat(command.getValue().currency()).isEqualTo(TRY);
        assertThat(command.getValue().openingDeposit().isZero()).isTrue();
    }

    @Test
    void openAccount_withMissingFields_isBadRequestWithErrorCode() {
        MvcTestResult result = postAccount("{}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void openAccount_withMalformedJson_isBadRequest() {
        assertThat(postAccount("{not json")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void openAccount_withWrongTcknCheckDigits_isRejectedByTheDomain() {
        MvcTestResult result = postAccount(requestJson("10000000147", "TRY", null));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_TCKN");
        assertThat(result).bodyText().doesNotContain("10000000147");
    }

    @Test
    void openAccount_withUnknownCurrencyCode_isBadRequest() {
        MvcTestResult result = postAccount(requestJson(SyntheticData.tckn(), "XYZ", null));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("UNSUPPORTED_CURRENCY");
    }

    @Test
    void openAccount_withThreeFractionDigits_isBadRequest() {
        assertThat(postAccount(requestJson(SyntheticData.tckn(), "TRY", "10.005")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAccount_returnsAccount() {
        Account account = TestAccounts.fundingAccount(TRY);
        given(accountQueryService.getAccount(account.id())).willReturn(account);

        MvcTestResult result = mvc.get().uri(ACCOUNTS + "/{id}", account.id().value()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.accountType").isEqualTo("FUNDING");
        assertThat(result).bodyJson().extractingPath("$.customerId").isNull();
    }

    @Test
    void getBalance_returnsBalance() {
        Account account = TestAccounts.customerAccount("42.10");
        given(accountQueryService.getBalance(account.id()))
                .willReturn(new AccountBalance(account.id(), account.iban(), account.balance()));

        MvcTestResult result = mvc.get().uri(ACCOUNTS + "/{id}/balance", account.id().value()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("42.10");
        assertThat(result).bodyJson().extractingPath("$.balance.currency").isEqualTo("TRY");
    }

    @Test
    void getBalance_ofUnknownAccount_isNotFound() {
        AccountId unknown = AccountId.newId();
        given(accountQueryService.getBalance(unknown)).willThrow(new AccountNotFoundException(unknown));

        MvcTestResult result = mvc.get().uri(ACCOUNTS + "/{id}/balance", unknown.value()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void ruleViolation_isUnprocessableContent() {
        AccountId accountId = AccountId.newId();
        given(accountQueryService.getAccount(accountId)).willThrow(new InsufficientFundsException(accountId));

        assertThat(mvc.get().uri(ACCOUNTS + "/{id}", accountId.value()).exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void concurrentModification_isConflict() {
        AccountId accountId = AccountId.newId();
        given(accountQueryService.getAccount(accountId)).willThrow(new OptimisticLockingFailureException("stale"));

        assertThat(mvc.get().uri(ACCOUNTS + "/{id}", accountId.value()).exchange())
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.errorCode").isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void unexpectedError_hidesDetailsFromTheClient() {
        AccountId accountId = AccountId.newId();
        given(accountQueryService.getAccount(accountId)).willThrow(new IllegalStateException("internal detail"));

        MvcTestResult result = mvc.get().uri(ACCOUNTS + "/{id}", accountId.value()).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INTERNAL_ERROR");
        assertThat(result).bodyText().doesNotContain("internal detail");
    }

    @Test
    void malformedAccountId_isBadRequest() {
        assertThat(mvc.get().uri(ACCOUNTS + "/not-a-uuid").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getLedgerEntries_returnsPage() {
        Account funding = TestAccounts.fundingAccount(TRY);
        Account customer = TestAccounts.customerAccount("0.00");
        Posting posting = Posting.between(PostingId.newId(), PostingType.OPENING_DEPOSIT, funding.id(),
                customer.id(), money("10.00"), OPENED_AT);
        List<LedgerEntry> lines = posting.entries().subList(1, 2);
        given(ledgerQueryService.getEntries(customer.id(), new PageQuery(0, 20)))
                .willReturn(new PageResult<>(lines, 0, 20, 1));

        MvcTestResult result = mvc.get().uri(ACCOUNTS + "/{id}/ledger-entries", customer.id().value()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.entries[0].direction").isEqualTo("CREDIT");
        assertThat(result).bodyJson().extractingPath("$.entries[0].postingType").isEqualTo("OPENING_DEPOSIT");
        assertThat(result).bodyJson().extractingPath("$.totalEntries").isEqualTo(1);
    }

    @Test
    void getLedgerEntries_withPageSizeAboveLimit_isBadRequest() {
        MvcTestResult result = mvc.get()
                .uri(ACCOUNTS + "/{id}/ledger-entries?size={size}", UUID.randomUUID(),
                        AccountController.MAX_PAGE_SIZE + 1)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void correlationId_isEchoedWhenValidAndGeneratedOtherwise() {
        AccountId accountId = AccountId.newId();
        given(accountQueryService.getAccount(accountId)).willReturn(TestAccounts.customerAccount("1.00"));

        assertThat(mvc.get().uri(ACCOUNTS + "/{id}", accountId.value())
                .header("X-Correlation-Id", "trace-123").exchange())
                .hasHeader("X-Correlation-Id", "trace-123");
        MvcTestResult generated = mvc.get().uri(ACCOUNTS + "/{id}", accountId.value())
                .header("X-Correlation-Id", "bad value\nwith newline").exchange();
        assertThat(generated.getResponse().getHeader("X-Correlation-Id"))
                .isNotEqualTo("bad value\nwith newline")
                .matches("[0-9a-f-]{36}");
    }

    private MvcTestResult postAccount(String json) {
        return mvc.post().uri(ACCOUNTS).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String requestJson(String tckn, String currency, String deposit) {
        String depositField = deposit == null ? "" : ", \"openingDeposit\": \"" + deposit + "\"";
        return """
                {"customerId": "%s", "holderName": "%s", "holderTckn": "%s", "currency": "%s"%s}
                """.formatted(UUID.randomUUID(), SyntheticData.fullName(), tckn, currency, depositField);
    }
}
