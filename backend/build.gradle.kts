plugins {
    java
    id("org.springframework.boot") version "4.0.0"
}

group = "cl.marcelapaz"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveBaseName.set("gestion-escolar")
}

tasks.bootRun {
    workingDir = rootProject.projectDir
}
