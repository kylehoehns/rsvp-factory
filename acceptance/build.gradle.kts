// The acceptance suite: end-to-end scenarios over HTTP against the real application.
// Each ticket adds scenarios for what it delivers; changing an existing scenario is high risk
// (see docs/adr/0002-acceptance-scenarios-grow-with-each-ticket.md).

plugins {
    id("rsvp.java-conventions")
}

dependencies {
    testImplementation(project(":app"))
    testImplementation(platform(versionCatalogs.named("libs").findLibrary("spring-modulith-bom").get()))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
