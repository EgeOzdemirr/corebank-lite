package io.github.egeozdemirr.corebank.account;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the layer rules in CLAUDE.md: api -> application -> domain, infrastructure implements application ports,
 * the domain depends on nothing but the JDK, and the application layer knows Spring only through its stereotype
 * and transaction annotations.
 */
@AnalyzeClasses(packages = "io.github.egeozdemirr.corebank.account",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..account.domain..";
    private static final String APPLICATION = "..account.application..";
    private static final String API = "..account.api..";
    private static final String INFRASTRUCTURE = "..account.infrastructure..";

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
                    "java..", DOMAIN, APPLICATION,
                    "org.springframework.stereotype..", "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule CONTROLLERS_LIVE_IN_API = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage(API);

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_REACH_PORTS = noClasses().that().resideInAPackage(API)
            .should().dependOnClassesThat().resideInAPackage("..account.application.port..");

    @ArchTest
    static final ArchRule ENTITIES_LIVE_IN_INFRASTRUCTURE = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule ONLY_THE_OUTBOX_KNOWS_THE_EVENT_CONTRACTS = noClasses()
            .that().resideOutsideOfPackage("..account.infrastructure.outbox..")
            .should().dependOnClassesThat().resideInAPackage("io.github.egeozdemirr.corebank.contracts..");

    @ArchTest
    static final ArchRule DEPENDENCIES_ARE_CONSTRUCTOR_INJECTED = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
}
