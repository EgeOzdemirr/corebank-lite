package io.github.egeozdemirr.corebank.transfer.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every pair of statuses (6 x 6 = 36 cases). The allowed steps are spelled out here independently of the production
 * table, so a change to the state machine has to change this list on purpose.
 */
class TransferStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "CREATED->PENDING_APPROVAL",
            "CREATED->APPROVED",
            "PENDING_APPROVAL->APPROVED",
            "PENDING_APPROVAL->FAILED",
            "APPROVED->POSTED",
            "APPROVED->FAILED",
            "POSTED->REVERSED");

    static Stream<Arguments> everyPairOfStatuses() {
        return Arrays.stream(TransferStatus.values()).flatMap(from -> Arrays.stream(TransferStatus.values())
                .map(to -> Arguments.of(from, to, ALLOWED.contains(from + "->" + to))));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @MethodSource("everyPairOfStatuses")
    void stateMachine_allowsExactlyTheDocumentedSteps(TransferStatus from, TransferStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }
}
