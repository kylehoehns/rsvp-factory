package dev.rsvpfactory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Project-wide rules that are mechanical enough to check rather than write down. Each {@code because} is the
 * remediation an agent reads when the rule fails, so it says what to do instead.
 */
@AnalyzeClasses(packages = "dev.rsvpfactory", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule timeComesFromAnInjectedClock = noClasses()
            .should()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .because("RSVP cutoffs and check-in windows must be testable: inject java.time.Clock and call"
                    + " Instant.now(clock) instead");

    @ArchTest
    static final ArchRule constructorInjectionOnly = noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .because("dependencies are final constructor parameters, so a test can build the class without Spring");

    @ArchTest
    static final ArchRule noStandardStreams = noClasses()
            .should()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .because("use an org.slf4j.Logger so output reaches the logs and traces the app already exports");

    @ArchTest
    static final ArchRule noJavaUtilLogging = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.util.logging..")
            .because("use an org.slf4j.Logger; Spring Boot routes it to the configured backend");
}
