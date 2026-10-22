package dev.rsvpfactory;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Module boundaries as failing tests, plus the generated architecture docs agents read. */
class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(RsvpFactoryApplication.class);

    @Test
    void modulesOnlyTalkThroughPublishedApisAndEvents() {
        // Fails when one module reaches into another module's internals or when modules form a cycle.
        // Fix by calling the other module's top-level (published) types, or by publishing an event.
        modules.verify();
    }

    @Test
    void everyModuleIsNullMarked() {
        for (ApplicationModule module : modules) {
            String packageName = module.getBasePackage().getName();
            assertThat(packageAnnotatedNullMarked(packageName))
                    .as(
                            "Module '%s' has no @NullMarked package-info.java. Add one to %s so NullAway checks it.",
                            module.getIdentifier(), packageName)
                    .isTrue();
        }
    }

    @Test
    void writeModuleDocs() {
        // A build output in docs/modules (gitignored): committed copies went stale when parallel PRs merged.
        new Documenter(modules, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }

    private static boolean packageAnnotatedNullMarked(String packageName) {
        try {
            return Class.forName(packageName + ".package-info").isAnnotationPresent(NullMarked.class);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
