package io.github.egeozdemirr.corebank.transfer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the layer rules in CLAUDE.md: api -> application -> domain, infrastructure implements application ports,
 * the domain depends on nothing but the JDK, the application layer knows Spring only through its stereotype and
 * transaction annotations, each contract module is known by one adapter, and time comes only from the Clock.
 */
@AnalyzeClasses(packages = "io.github.egeozdemirr.corebank.transfer",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..transfer.domain..";
    private static final String APPLICATION = "..transfer.application..";
    private static final String API = "..transfer.api..";
    private static final String INFRASTRUCTURE = "..transfer.infrastructure..";

    @ArchTest
    static final ArchRule LAYERS_RESPECT_DEPENDENCY_DIRECTION = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy(API)
            .layer("Application").definedBy(APPLICATION)
            .layer("Domain").definedBy(DOMAIN)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Api", "Infrastructure");

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = classes().that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN);

    @ArchTest
    static final ArchRule APPLICATION_USES_ONLY_STEREOTYPE_AND_TRANSACTION_ANNOTATIONS = classes()
            .that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "org.slf4j..", DOMAIN, APPLICATION,
                    "org.springframework.stereotype..", "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule CONTROLLERS_LIVE_IN_API = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage(API);

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_REACH_PORTS = noClasses().that().resideInAPackage(API)
            .should().dependOnClassesThat().resideInAPackage("..transfer.application.port..");

    @ArchTest
    static final ArchRule ENTITIES_LIVE_IN_INFRASTRUCTURE = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule ONLY_THE_OUTBOX_KNOWS_THE_EVENT_CONTRACTS = noClasses()
            .that().resideOutsideOfPackage("..transfer.infrastructure.outbox..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.github.egeozdemirr.corebank.contracts", "io.github.egeozdemirr.corebank.contracts.events..");

    @ArchTest
    static final ArchRule ONLY_THE_GRPC_ADAPTER_KNOWS_THE_GRPC_CONTRACTS = noClasses()
            .that().resideOutsideOfPackage("..transfer.infrastructure.grpc..")
            .should().dependOnClassesThat().resideInAPackage("io.github.egeozdemirr.corebank.contracts.grpc..");

    @ArchTest
    static final ArchRule DEPENDENCIES_ARE_CONSTRUCTOR_INJECTED = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * Time comes only from the injected {@link Clock}: it is UTC, ticks in microseconds like PostgreSQL and can be
     * fixed in tests. Reading the system time directly bypasses all three.
     */
    @ArchTest
    static final ArchRule TIME_COMES_ONLY_FROM_THE_CLOCK = noClasses()
            .should().callMethodWhere(DescribedPredicate.describe(
                    "reads the system time instead of the injected Clock",
                    ArchitectureTest::readsSystemTime));

    @ArchTest
    static final ArchRule ONLY_CONFIGURATION_CREATES_THE_SYSTEM_CLOCK = noClasses()
            .that().resideOutsideOfPackage("..transfer.infrastructure.config..")
            .should().callMethodWhere(DescribedPredicate.describe(
                    "creates a system clock",
                    call -> call.getTargetOwner().isEquivalentTo(Clock.class)
                            && call.getName().startsWith("system")));

    private static final Set<Class<?>> DATE_TIME_TYPES = Set.of(Instant.class, OffsetDateTime.class,
            ZonedDateTime.class, LocalDateTime.class, LocalDate.class, LocalTime.class);

    private static boolean readsSystemTime(JavaCall<?> call) {
        boolean noArgumentNow = "now".equals(call.getName()) && call.getTarget().getRawParameterTypes().isEmpty()
                && DATE_TIME_TYPES.stream().anyMatch(type -> call.getTargetOwner().isEquivalentTo(type));
        boolean currentTimeMillis = call.getTargetOwner().isEquivalentTo(System.class)
                && "currentTimeMillis".equals(call.getName());
        return noArgumentNow || currentTimeMillis;
    }
}
