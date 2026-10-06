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
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.4.8"
	id("net.ltgt.errorprone") version "5.1.0"
	id("org.owasp.dependencycheck") version "12.2.2"
	id("de.aaschmid.cpd") version "3.5"
	id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.betterreads"
version = "0.0.1-SNAPSHOT"
description = "BetterReads v2 - Book tracking and recommendation platform"

extra["netty.version"] = "4.2.15.Final"
extra["tomcat.version"] = "11.0.22"
extra["postgresql.version"] = "42.7.11"

// Generates build-info.properties so the version shows on /actuator/info.
springBoot {
	buildInfo()
}

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

val mainSourceSet = sourceSets.main.get()
val testSourceSet = sourceSets.test.get()

val liveTestSourceSet = sourceSets.create("liveTest") {
	compileClasspath += mainSourceSet.output + testSourceSet.output
	runtimeClasspath += output + compileClasspath
}

val localDbVerificationSourceSet = sourceSets.create("localDbVerification") {
	compileClasspath += mainSourceSet.output + testSourceSet.output
	runtimeClasspath += output + compileClasspath
}

configurations.named(liveTestSourceSet.implementationConfigurationName) {
	extendsFrom(configurations.testImplementation.get())
}
configurations.named(liveTestSourceSet.runtimeOnlyConfigurationName) {
	extendsFrom(configurations.testRuntimeOnly.get())
}
configurations.named(localDbVerificationSourceSet.implementationConfigurationName) {
	extendsFrom(configurations.testImplementation.get())
}
configurations.named(localDbVerificationSourceSet.runtimeOnlyConfigurationName) {
	extendsFrom(configurations.testRuntimeOnly.get())
}

repositories {
	mavenCentral()
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	// Boot 4 ships Flyway auto-configuration in the starter, and the postgresql dialect is a separate module
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("tools.jackson.dataformat:jackson-dataformat-xml")

	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
	implementation("com.bucket4j:bucket4j_jdk17-lettuce:8.14.0")
	implementation("com.meilisearch.sdk:meilisearch-java:0.20.1")
	implementation("io.minio:minio:8.5.17")
	implementation("net.coobird:thumbnailator:0.4.20")
	implementation("org.jsoup:jsoup:1.23.2")

	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	implementation("org.jspecify:jspecify:1.0.0")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	errorprone("com.google.errorprone:error_prone_core:2.48.0")
	errorprone("com.uber.nullaway:nullaway:0.13.1")
	spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.wiremock:wiremock-standalone:3.13.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
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

cpd {
	toolVersion = "7.16.0"
	language = "java"
	minimumTokenCount = 75
	isIgnoreFailures = false
}

// entity getter/setter runs trip CPD, so the entity files are excluded
tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
	source = files("src/main/java").asFileTree.matching {
		exclude(
			"**/Book.java", "**/Author.java", "**/BookSubject.java", "**/BookAward.java", "**/PendingBook.java",
			"**/User.java", "**/Comment.java", "**/ShelfEntry.java", "**/Review.java", "**/*Token.java"
		)
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
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

// ---------------------------------------------------------------------------
// PIT mutation testing, opt-in: ./gradlew pitest -PpitClasses='com.betterreads.text.*'
// ---------------------------------------------------------------------------
pitest {
	pitestVersion = "1.30.0"
	junit5PluginVersion = "1.2.3"
	addJUnitPlatformLauncher = false
	targetClasses.set(providers.gradleProperty("pitClasses").orElse("com.betterreads.*").map { it.split(",") })
	targetTests.set(providers.gradleProperty("pitTests").orElse("com.betterreads.*").map { it.split(",") })
	excludedTestClasses.set(
		providers.gradleProperty("pitExcludedTests").orElse("*IntegrationTest,com.betterreads.ArchitectureTest")
			.map { it.split(",") }
	)
	threads = providers.gradleProperty("pitThreads").orElse("6").get().toInt()
	timeoutConstInMillis = providers.gradleProperty("pitTimeoutMillis").orElse("4000").get().toInt()
	outputFormats.set(listOf("XML", "HTML"))
	timestampedReports = false
	jvmArgs.set(listOf("-Xmx1g", "-Dmail.outbox.worker-enabled=false"))
}

// ---------------------------------------------------------------------------
// OWASP Dependency-Check (vulnerable dependency scanning)
// ---------------------------------------------------------------------------
dependencyCheck {
	nvd {
		apiKey = providers.environmentVariable("NVD_API_KEY").orNull
	}
	failBuildOnCVSS = 7.0f
	failBuildOnUnusedSuppressionRule = true
	formats = listOf("HTML", "JSON")
	suppressionFile = "config/dependency-check/suppressions.xml"
	analyzers {
		assemblyEnabled = false
		nuspecEnabled = false
		nugetconfEnabled = false
		nodeEnabled = false
		nodeAuditEnabled = false
		retirejs {
			enabled = false
		}
	}
}

// ---------------------------------------------------------------------------
// Test config
// ---------------------------------------------------------------------------
// the test JVM does not inherit the parent environment, so the opt-in suites get their keys and flags passed in by name
fun Test.forwardEnvironmentVariables(names: Iterable<String>) {
	names.forEach { name ->
		project.providers.environmentVariable(name).orNull?.let { environment(name, it) }
	}
}

val liveTestEnvironmentVariables = listOf(
	"DESCRIPTION_LIVE",
	"GOOGLE_BOOKS_API_KEY",
	"HARDCOVER_BEARER_TOKEN",
	"RUN_LOC_LIVE",
	"RUN_OPENLIBRARY_LIVE",
	"RUN_WEB_SEARCH_LIVE",
	"CLAUDE_CODE_OAUTH_TOKEN"
)

val localDbVerificationEnvironmentVariables = listOf(
	"DB_HOST",
	"DB_PORT",
	"DB_NAME",
	"DB_USERNAME",
	"DB_APP_USERNAME",
	"GOOGLE_BOOKS_API_KEY",
	"HARDCOVER_BEARER_TOKEN",
	"RUN_LOCAL_DB_VERIFICATION",
	"RUN_LOC_LIVE"
)

tasks.register<Test>("liveTest") {
	description = "Runs tests against live external services."
	group = "verification"
	testClassesDirs = liveTestSourceSet.output.classesDirs
	classpath = liveTestSourceSet.runtimeClasspath
	dependsOn(liveTestSourceSet.classesTaskName)
	shouldRunAfter(tasks.test)
	forwardEnvironmentVariables(liveTestEnvironmentVariables)
}

tasks.register<Test>("localDbVerification") {
	description = "Runs operator checks against the local database."
	group = "verification"
	testClassesDirs = localDbVerificationSourceSet.output.classesDirs
	classpath = localDbVerificationSourceSet.runtimeClasspath
	dependsOn(localDbVerificationSourceSet.classesTaskName)
	shouldRunAfter(tasks.test)
	forwardEnvironmentVariables(localDbVerificationEnvironmentVariables)
}

tasks.register<Test>("openApiSpec") {
	description = "Regenerates openapi.yaml from the running controllers."
	group = "documentation"
	testClassesDirs = testSourceSet.output.classesDirs
	classpath = testSourceSet.runtimeClasspath
	filter { includeTestsMatching("com.betterreads.web.OpenApiEnvelopeTest.shouldMatchCommittedSpec") }
	systemProperty("openapi.write", "true")
	outputs.upToDateWhen { false }
}

val testCredentials = mapOf(
	"DB_PASSWORD" to "test-owner-password",
	"DB_APP_PASSWORD" to "test-app-password",
	"MEILI_MASTER_KEY" to "test-meili-master-key",
	"MINIO_ACCESS_KEY" to "test-minio-access-key",
	"MINIO_SECRET_KEY" to "test-minio-secret-key"
)

tasks.withType<Test> {
	useJUnitPlatform()

	// application.yml has no fallback for these, so a test context needs a value to start
	testCredentials.forEach { (name, value) ->
		environment(name, providers.environmentVariable(name).getOrElse(value))
	}

	// ArchUnit's class graph of the whole app overflows the default fork heap
	maxHeapSize = "2g"

	// the outbox worker and deletion sweep race the Testcontainers Postgres shutdown and log a 30s Hikari timeout after the test passed
	systemProperty("mail.outbox.worker-enabled", "false")
	systemProperty("betterreads.auth.deletion.scheduler-enabled", "false")
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

// the opt-in suites compile and get static analysis here, and run only via their own tasks
tasks.named("check") {
	dependsOn(
		tasks.jacocoTestCoverageVerification,
		tasks.named(liveTestSourceSet.classesTaskName),
		tasks.named(localDbVerificationSourceSet.classesTaskName),
		tasks.named("checkstyleLiveTest"),
		tasks.named("checkstyleLocalDbVerification"),
		tasks.named("pmdLiveTest"),
		tasks.named("pmdLocalDbVerification"),
		tasks.named("spotbugsLiveTest"),
		tasks.named("spotbugsLocalDbVerification")
	)
	val nvdApiKey = providers.environmentVariable("NVD_API_KEY").orNull
	if (!nvdApiKey.isNullOrBlank()) {
		dependsOn(tasks.named("dependencyCheckAnalyze"))
	}
}
