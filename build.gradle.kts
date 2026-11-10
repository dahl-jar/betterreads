import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd

buildscript {
	configurations.classpath {
		resolutionStrategy.activateDependencyLocking()
	}
	dependencies {
		constraints {
			classpath("org.apache.commons:commons-lang3:3.18.0")
			classpath("org.apache.httpcomponents.client5:httpclient5:5.6.3")
			classpath("org.apache.httpcomponents.core5:httpcore5:5.4.3")
			classpath("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
		}
	}
}

plugins {
	java
	jacoco
	checkstyle
	pmd
	id("org.springframework.boot") version "4.0.9"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.4.8"
	id("net.ltgt.errorprone") version "5.1.0"
	id("de.aaschmid.cpd") version "3.5"
	id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.betterreads"
version = "0.0.1-SNAPSHOT"
description = "BetterReads v2 - Book tracking and recommendation platform"

springBoot {
	buildInfo {
		excludes.set(setOf("time"))
	}
}

tasks.bootJar {
	archiveFileName = "app.jar"
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

dependencyLocking {
	lockAllConfigurations()
	lockMode = LockMode.STRICT
}

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
	implementation("io.minio:minio:8.6.0")
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
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.wiremock:wiremock-standalone:3.13.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")

	constraints {
		implementation("com.google.guava:guava:33.7.2-jre")
		implementation("org.json:json:20260719")
		implementation("org.bouncycastle:bcprov-jdk18on:1.85")
		implementation("org.xerial.snappy:snappy-java:1.1.10.9")
	}
}

val fixedGuava = "com.google.guava:guava:33.7.2-jre"
val httpClientVersion = "5.6.3"
val httpCoreVersion = "5.4.3"
val fixedHttpClient = listOf(
	"org.apache.httpcomponents.client5:httpclient5:$httpClientVersion",
	"org.apache.httpcomponents.core5:httpcore5:$httpCoreVersion",
	"org.apache.httpcomponents.core5:httpcore5-h2:$httpCoreVersion",
)
val toolPins = mapOf(
	"cpd" to listOf(fixedGuava, "org.jline:jline:3.25.0", "org.jsoup:jsoup:1.23.2", "org.mozilla:rhino:1.7.15.1") + fixedHttpClient,
	"pmd" to fixedHttpClient,
	"checkstyle" to listOf(fixedGuava, "org.codehaus.plexus:plexus-utils:3.6.1"),
	"annotationProcessor" to listOf(fixedGuava),
	"testAnnotationProcessor" to listOf(fixedGuava),
	"liveTestAnnotationProcessor" to listOf(fixedGuava),
	"localDbVerificationAnnotationProcessor" to listOf(fixedGuava),
)

dependencies {
	constraints {
		toolPins.forEach { (configuration, coordinates) ->
			coordinates.forEach { add(configuration, it) }
		}
	}
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

tasks.withType<JavaCompile>().configureEach {
	options.errorprone.disableWarningsInGeneratedCode.set(true)
	options.errorprone.option("NullAway:AnnotatedPackages", "com.betterreads")
}

tasks.named<JavaCompile>("compileJava") {
	options.errorprone.error("NullAway")
}

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
				minimum = "0.64".toBigDecimal()
			}
		}
	}
}

pitest {
	pitestVersion = "1.30.0"
	junit5PluginVersion = "1.2.3"
	addJUnitPlatformLauncher = false
	targetClasses.set(providers.gradleProperty("pitClasses").orElse("com.betterreads.*").map { it.split(",") })
	targetTests.set(providers.gradleProperty("pitTests").orElse("com.betterreads.*").map { it.split(",") })
	excludedTestClasses.set(
		providers.gradleProperty("pitExcludedTests").orElse("com.betterreads.ArchitectureTest")
			.map { it.split(",") }
	)
	threads = providers.gradleProperty("pitThreads").orElse("6").get().toInt()
	timeoutConstInMillis = providers.gradleProperty("pitTimeoutMillis").orElse("4000").get().toInt()
	outputFormats.set(listOf("XML", "HTML"))
	timestampedReports = false
	jvmArgs.set(listOf("-Xmx1g", "-Dmail.outbox.worker-enabled=false"))
}

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
	outputs.doNotCacheIf("it calls live external services") { true }
}

tasks.register<Test>("localDbVerification") {
	description = "Runs operator checks against the local database."
	group = "verification"
	testClassesDirs = localDbVerificationSourceSet.output.classesDirs
	classpath = localDbVerificationSourceSet.runtimeClasspath
	dependsOn(localDbVerificationSourceSet.classesTaskName)
	shouldRunAfter(tasks.test)
	forwardEnvironmentVariables(localDbVerificationEnvironmentVariables)
	outputs.doNotCacheIf("it checks the configured database") { true }
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

	testCredentials.forEach { (name, value) ->
		environment(name, providers.environmentVariable(name).getOrElse(value))
	}

	maxHeapSize = "2g"

	systemProperty("mail.outbox.worker-enabled", "false")
	systemProperty("betterreads.auth.deletion.scheduler-enabled", "false")
}

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

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
}

private fun Task.directDependencies(): Set<Task> = taskDependencies.getDependencies(this)

private fun Task.dependsTransitivelyOn(taskName: String): Boolean =
	directDependencies().any { it.name == taskName || it.dependsTransitivelyOn(taskName) }

private val lintTask = tasks.register("lint") {
	description = "Compiles main and test code and runs Checkstyle, PMD and SpotBugs on it."
	group = "verification"
	dependsOn(
		"compileJava",
		"compileTestJava",
		"checkstyleMain",
		"checkstyleTest",
		"pmdMain",
		"pmdTest",
		"spotbugsMain",
		"spotbugsTest"
	)
}

private val testJobTask = tasks.register("testJob") {
	description = "Runs the tests and checks the 0.64 coverage rule."
	group = "verification"
	dependsOn(tasks.test, tasks.jacocoTestCoverageVerification)
}

tasks.register("extraChecks") {
	description = "Runs every check task that lint and testJob do not run."
	group = "verification"
	dependsOn(provider {
		val coveredTasks = lintTask.get().directDependencies() + testJobTask.get().directDependencies()
		tasks.check.get().directDependencies()
			.filterNot { it in coveredTasks }
			.onEach { task ->
				if (task.dependsTransitivelyOn(JavaPlugin.TEST_TASK_NAME)) {
					throw GradleException(
						"extraChecks must not run :test, and ${task.path} depends on it. " +
							"Add ${task.name} to testJob in build.gradle.kts."
					)
				}
			}
	})
}

extra["httpclient5.version"] = httpClientVersion
extra["httpcore5.version"] = httpCoreVersion
