package io.github.egeozdemirr.corebank.transfer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Guards the rules in CLAUDE.md that apply to the domain model: it depends on nothing but the JDK, and it never
 * reads the system time (time is passed in, so it can be fixed in tests and matches PostgreSQL's precision).
 * Layer rules for api, application and infrastructure arrive with those layers.
 */
@AnalyzeClasses(packages = "io.github.egeozdemirr.corebank.transfer",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..transfer.domain..";

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = classes().that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN);

    @ArchTest
    static final ArchRule TIME_IS_PASSED_IN_NOT_READ = noClasses()
            .should().callMethodWhere(DescribedPredicate.describe(
                    "reads the system time instead of an injected Clock",
                    ArchitectureTest::readsSystemTime));

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
