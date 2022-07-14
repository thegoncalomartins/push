import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

val mockitoKotlinVersion: String by extra("4.0.0")
val mockitoJunitJupiterVersion: String by extra("4.6.0")
val springJaegerCloudStarterVersion: String by extra("3.3.1")
val logstashLogbackEncoderVersion by extra("7.2")
val janinoVersion: String by extra("3.1.7")

plugins {
    id("org.springframework.boot") version "2.7.0"
    id("org.springframework.experimental.aot") version "0.12.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.20"
    kotlin("plugin.spring") version "1.6.20"
}

group = "dev.goncalomartins"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/release") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentracing.contrib:opentracing-spring-jaeger-cloud-starter:$springJaegerCloudStarterVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("org.codehaus.janino:janino:$janinoVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoJunitJupiterVersion")
}

sourceSets {
    create("integrationTest") {
        withConvention(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet::class) {
            kotlin.srcDir("src/integrationTest/kotlin")
            resources.srcDir("src/integrationTest/resources")
            compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
            runtimeClasspath += output + compileClasspath + sourceSets["test"].runtimeClasspath
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )

        showExceptions = true
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true

        showStandardStreams = true

        afterSuite(
            KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
                if (desc.parent == null) {
                    println(
                        "${result.resultType} " +
                            "(${result.testCount} tests, " +
                            "${result.successfulTestCount} successes, " +
                            "${result.failedTestCount} failures, " +
                            "${result.skippedTestCount} skipped)"
                    )
                }
            })
        )
    }
}

task<Test>("integrationTest") {
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
}

tasks.getByName<BootBuildImage>("bootBuildImage") {
    builder = "paketobuildpacks/builder:tiny"
    environment = mapOf(
        "BP_NATIVE_IMAGE" to "true"
    )
}

springAot {
    removeXmlSupport.set(false)
}
