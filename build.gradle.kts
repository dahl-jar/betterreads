import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd

plugins {
	java
	jacoco
	checkstyle
	pmd
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.4.8"
	id("net.ltgt.errorprone") version "5.1.0"
	id("org.owasp.dependencycheck") version "12.2.0"
	id("de.aaschmid.cpd") version "3.5"
}

group = "com.betterreads"
version = "0.0.1-SNAPSHOT"
description = "BetterReads v2 - Book tracking and recommendation platform"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
dependencies {
	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	// Prometheus metrics for /actuator/prometheus
	implementation("io.micrometer:micrometer-registry-prometheus")

	// OpenAPI / Swagger UI: auto-generates /v3/api-docs and /swagger-ui.html
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

	// Database & migration
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// Cache
	implementation("com.github.ben-manes.caffeine:caffeine")

	// JSON
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

	// XML (Library of Congress SRU returns MARC/MODS records)
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

	// JWT (HS256 signing for stateless auth)
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// Rate limiting (token bucket per IP)
	implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")

	// Meilisearch (official Java client) — catalog full-text search engine
	implementation("com.meilisearch.sdk:meilisearch-java:0.14.5")

	// Spring Boot @ConfigurationProperties metadata generator
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Null safety annotations (JSpecify)
	implementation("org.jspecify:jspecify:1.0.0")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Error Prone + NullAway (compile-time bug detection)
	errorprone("com.google.errorprone:error_prone_core:2.48.0")
	errorprone("com.uber.nullaway:nullaway:0.13.1")
	spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.wiremock:wiremock-standalone:3.9.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// ArchUnit (architecture rule enforcement)
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

checkstyle {
	toolVersion = "13.3.0"
	configFile = file("config/checkstyle/checkstyle.xml")
	isIgnoreFailures = false
	maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

pmd {
	toolVersion = "7.16.0"
	ruleSetFiles = files("config/pmd/pmd.xml")
	ruleSets = emptyList()
	isConsoleOutput = true
	isIgnoreFailures = false
	incrementalAnalysis.set(true)
}

// Copy-paste detection. JPA entity accessors are identical by ORM necessity, not copied
// logic, so the entity packages are excluded rather than tripping CPD on getter/setter runs.
cpd {
	toolVersion = "7.16.0"
	language = "java"
	minimumTokenCount = 75
	isIgnoreFailures = false
}

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
	source = files("src/main/java").asFileTree.matching {
		exclude("**/entity/**", "**/token/*Token.java", "**/refresh/*Token.java")
	}
}

tasks.withType<Pmd>().configureEach {
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

spotbugs {
	toolVersion.set("4.9.8")
	ignoreFailures.set(false)
	showStackTraces.set(true)
	showProgress.set(true)
	effort.set(Effort.MAX)
	reportLevel.set(Confidence.LOW)
	maxHeapSize.set("1g")
	excludeFilter.set(file("config/spotbugs/exclude.xml"))
}

tasks.withType<SpotBugsTask>().configureEach {
	reports.create("html") {
		required.set(true)
	}
	reports.create("xml") {
		required.set(true)
	}
}

// ---------------------------------------------------------------------------
// Error Prone + NullAway (compiler-level checks)
// ---------------------------------------------------------------------------
tasks.withType<JavaCompile>().configureEach {
	options.errorprone.disableWarningsInGeneratedCode.set(true)
	options.errorprone.option("NullAway:AnnotatedPackages", "com.betterreads")
}

tasks.named<JavaCompile>("compileJava") {
	options.errorprone.error("NullAway")
}

// ---------------------------------------------------------------------------
// JaCoCo (test coverage enforcement)
// ---------------------------------------------------------------------------
jacoco {
	toolVersion = "0.8.14"
}

val jacocoCoverageExcludes = listOf(
	"com/betterreads/BetterReadsApplication.class"
)

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	classDirectories.setFrom(
		files(classDirectories.files.map { directory ->
			fileTree(directory) {
				exclude(jacocoCoverageExcludes)
			}
		})
	)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.jacocoTestCoverageVerification {
	classDirectories.setFrom(
		files(classDirectories.files.map { directory ->
			fileTree(directory) {
				exclude(jacocoCoverageExcludes)
			}
		})
	)
	violationRules {
		rule {
			limit {
				minimum = "0.80".toBigDecimal() // 80% line coverage minimum
			}
		}
	}
}

// ---------------------------------------------------------------------------
// OWASP Dependency-Check (vulnerable dependency scanning)
// ---------------------------------------------------------------------------
dependencyCheck {
	nvd {
		apiKey = providers.environmentVariable("NVD_API_KEY").orNull
	}
	failBuildOnCVSS = 7.0f // fail on HIGH+ severity
	failBuildOnUnusedSuppressionRule = true
	formats = listOf("HTML", "JSON")
	suppressionFile = "config/dependency-check/suppressions.xml"
}

// ---------------------------------------------------------------------------
// Test config
// ---------------------------------------------------------------------------
tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)

	// Disable @Scheduled jobs during tests. Without these, the mail-outbox worker and the
	// account-deletion sweep tick on a shared scheduler thread and can race the Testcontainers
	// Postgres shutdown at the end of an integration test, producing a noisy 30s Hikari timeout
	// long after the test has already passed.
	systemProperty("mail.outbox.worker-enabled", "false")
	systemProperty("betterreads.auth.deletion.scheduler-enabled", "false")

	// Forward env vars the opt-in live/local-DB verification tests need. Gradle does not pass the
	// parent environment to the test JVM, so the compose-DB credentials and source API keys would
	// fall back to their application.yml defaults and fail to authenticate. Only forwarded when set.
	listOf(
		"DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
		"DB_APP_USERNAME", "DB_APP_PASSWORD",
		"HARDCOVER_BEARER_TOKEN", "GOOGLE_BOOKS_API_KEY",
		"RUN_LOCAL_DB_VERIFICATION", "RUN_OPENLIBRARY_LIVE",
	).forEach { name ->
		System.getenv(name)?.let { environment(name, it) }
	}
}

// Wire coverage verification into check
tasks.named("check") {
	dependsOn(tasks.jacocoTestCoverageVerification)
	val nvdApiKey = providers.environmentVariable("NVD_API_KEY").orNull
	if (!nvdApiKey.isNullOrBlank()) {
		dependsOn(tasks.named("dependencyCheckAnalyze"))
	}
}
