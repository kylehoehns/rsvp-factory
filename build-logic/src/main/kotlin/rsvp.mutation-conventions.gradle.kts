// Mutation testing: checks that the tests would notice if the code were wrong.
// Agent-written tests often run code without asserting anything useful; surviving mutants expose that.

plugins {
    java
    id("info.solidsoft.pitest")
}

val libs = versionCatalogs.named("libs")
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

pitest {
    pitestVersion = version("pitest")
    junit5PluginVersion = version("pitest-junit5")
    // CI passes -PpitTargets=dev.rsvpfactory.rsvp.Waitlist*,... with the classes a PR changed, so a PR
    // only pays for what it touched. (PIT's own incremental mode now needs a commercial plugin.)
    targetClasses = providers
            .gradleProperty("pitTargets")
            .map { it.split(",").map(String::trim).toSet() }
            .orElse(setOf("dev.rsvpfactory.*"))
    // Mutate only the target classes, but always run every test: PIT otherwise limits the tests it runs to
    // targetClasses too, so with -PpitTargets it ran none of them and every mutant was NO_COVERAGE.
    targetTests = setOf("dev.rsvpfactory.*")
    excludedTestClasses = setOf("*ModularityTests", "*ArchitectureTests", "*ApplicationTests")
    threads = Runtime.getRuntime().availableProcessors()
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    failWhenNoMutations = false
    // CI passes -PpitThreshold for a PR's changed classes; locally the report is advisory.
    mutationThreshold = providers.gradleProperty("pitThreshold").map(String::toInt).orElse(0)
}
