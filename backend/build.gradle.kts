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
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
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

// Local Docker stack with PostgreSQL exposed on localhost, using the repository's .env.
val repositoryRoot = layout.projectDirectory.dir("..").asFile
val composeArguments = listOf("-f", "deploy/compose.yml", "-f", "deploy/compose.dev.yml", "--env-file", ".env")

// A daemon started by an IDE can carry a minimal PATH (/usr/bin:/bin), and the JVM resolves executables against the
// PATH it started with, so Docker tools are resolved to absolute paths and run with a full PATH (credential helpers).
val toolPath = (providers.environmentVariable("PATH").getOrElse("").split(File.pathSeparator) +
    listOf("/opt/homebrew/bin", "/usr/local/bin", "${System.getProperty("user.home")}/.docker/bin"))
    .filter { it.isNotBlank() }
    .distinct()

fun executable(name: String): String =
    toolPath.map { File(it, name) }.firstOrNull { it.canExecute() }?.absolutePath ?: name

// `docker compose` (plugin) when it works, otherwise the standalone `docker-compose`.
fun Exec.dockerCompose(vararg arguments: String) {
    val docker = executable("docker")
    val plugin = providers.exec {
        commandLine(docker, "compose", "version")
        environment("PATH", toolPath.joinToString(File.pathSeparator))
        isIgnoreExitValue = true
    }
    val command = if (plugin.result.get().exitValue == 0) listOf(docker, "compose") else listOf(executable("docker-compose"))
    environment("PATH", toolPath.joinToString(File.pathSeparator))
    commandLine(command + composeArguments + arguments)
}

tasks.register<Exec>("dockerUp") {
    description = "Builds and starts the full stack with PostgreSQL on localhost:5432 and waits until it is healthy."
    group = "docker"
    workingDir = repositoryRoot
    doFirst {
        check(repositoryRoot.resolve(".env").isFile) { "Create .env in the repository root first (see .env.example)." }
        dockerCompose("up", "-d", "--build", "--wait")
    }
    finalizedBy("dockerLogs")
}

// Runs after dockerUp even when it fails, so a container that never became healthy shows why.
// Ctrl+C stops following; the stack keeps running.
tasks.register<Exec>("dockerLogs") {
    description = "Follows the backend (app) container logs."
    group = "docker"
    workingDir = repositoryRoot
    doFirst {
        dockerCompose("logs", "-f", "--tail", "200", "app")
    }
}

tasks.register<Exec>("dockerDeployWeb") {
    description = "Rebuilds and restarts only the frontend (web) container, leaving the app and database running."
    group = "docker"
    workingDir = repositoryRoot
    doFirst {
        check(repositoryRoot.resolve(".env").isFile) { "Create .env in the repository root first (see .env.example)." }
        dockerCompose("up", "-d", "--build", "--no-deps", "--wait", "web")
    }
}

tasks.register<Exec>("dockerDown") {
    description = "Stops and removes the Docker stack; data stays in the named volumes."
    group = "docker"
    workingDir = repositoryRoot
    doFirst {
        dockerCompose("down")
    }
}

// Development only: drops and recreates the `learning` database in the stack's PostgreSQL container. The app
// recreates the schema through Flyway on its next start; media volumes are left alone.
tasks.register<Exec>("reset-db") {
    description = "Drops and recreates the Docker stack's learning database (all data is lost)."
    group = "docker"
    workingDir = repositoryRoot
    doFirst {
        check(repositoryRoot.resolve(".env").isFile) { "Create .env in the repository root first (see .env.example)." }
        dockerCompose(
            "exec", "-T", "postgres",
            "psql", "-U", "learning", "-d", "postgres", "-v", "ON_ERROR_STOP=1",
            "-c", "drop database if exists learning with (force)",
            "-c", "create database learning owner learning",
        )
    }
}
