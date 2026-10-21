// The Spring Boot application: java conventions plus the Boot plugin and the Modulith BOM.

plugins {
    id("rsvp.java-conventions")
    id("org.springframework.boot")
}

val libs = versionCatalogs.named("libs")

dependencies {
    // developmentOnly doesn't extend implementation, so it needs the BOMs too.
    developmentOnly(platform(libs.findLibrary("spring-boot-bom").get()))
    implementation(platform(libs.findLibrary("spring-modulith-bom").get()))
    testImplementation(platform(libs.findLibrary("spring-modulith-bom").get()))
}
