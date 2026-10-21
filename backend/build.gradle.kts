plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.saasresearcher"
version = "0.1.0-SNAPSHOT"
description = "Self-hosted learning portal for tutors and small schools"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Security patch overrides of Boot 4.1.1 managed versions; drop when Boot ships these or newer.
extra["tomcat.version"] = "11.0.27"
extra["jackson-bom.version"] = "3.1.8"

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:2.56.0")
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20261011.1")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("com.google.zxing:core:3.5.4")
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit tests (*Test) run in `test`, integration tests (*IT, Testcontainers) in `integrationTest`; `check` runs both.
tasks.test {
    useJUnitPlatform()
    filter {
        includeTestsMatching("*Test")
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the integration tests (*IT)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*IT")
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTest)
}

// Only the executable Boot jar is distributed.
tasks.jar {
    enabled = false
}
