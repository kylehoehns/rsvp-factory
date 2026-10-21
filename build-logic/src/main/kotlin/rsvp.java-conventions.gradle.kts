// Every Java subproject: JDK 25, formatting, null-safety, and tests on JUnit Platform.
// These are the in-loop brakes: they fail in seconds with a file and line number.

import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform(lib("spring-boot-bom")))
    implementation(lib("jspecify"))
    errorprone(lib("errorprone-core"))
    errorprone(lib("nullaway"))
    testImplementation(platform(lib("spring-boot-bom")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Packages opt in with @NullMarked in package-info.java; inside them, null must be declared.
        check("NullAway", if (name.contains("Test")) CheckSeverity.OFF else CheckSeverity.ERROR)
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:OnlyNullMarked", "true")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat(version("palantir-java-format")).formatJavadoc(true)
        removeUnusedImports()
        forbidWildcardImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
