package dev.rsvpfactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
        sortRelationLines(Path.of("../docs/modules"));
    }

    /**
     * The Documenter emits relations in hash order, which varies between runs; sort so the committed copy is stable.
     */
    private static void sortRelationLines(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".puml")).toList()) {
                List<String> lines = Files.readAllLines(file);
                List<String> relations = lines.stream()
                        .filter(l -> l.startsWith("Rel("))
                        .sorted()
                        .toList();
                List<String> sorted = new ArrayList<>(lines);
                int next = 0;
                for (int i = 0; i < sorted.size(); i++) {
                    if (sorted.get(i).startsWith("Rel(")) {
                        sorted.set(i, relations.get(next++));
                    }
                }
                Files.write(file, sorted);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean packageAnnotatedNullMarked(String packageName) {
        try {
            return Class.forName(packageName + ".package-info").isAnnotationPresent(NullMarked.class);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
