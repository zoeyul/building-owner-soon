
plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("kapt") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    id("org.springframework.boot") version "3.4.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    autoCorrect = false
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        include("**/src/**")
    }
}

group = "com.bos"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // spring
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.mariadb.jdbc:mariadb-java-client")

    // kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // h2
    implementation("org.mariadb:r2dbc-mariadb:1.1.4")

    // jwt
    implementation("io.jsonwebtoken:jjwt-api:${property("JJWT_VERSION")}")
    implementation("io.jsonwebtoken:jjwt-impl:${property("JJWT_VERSION")}")
    implementation("io.jsonwebtoken:jjwt-jackson:${property("JJWT_VERSION")}") // JSON 처리

    // aws
    implementation("software.amazon.awssdk:s3:2.29.39")
    implementation("software.amazon.awssdk:netty-nio-client:2.29.39")

    // util
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // mapstruct
    implementation("org.mapstruct:mapstruct:${property("MAPSTRUCT_VERSION")}")
    kapt("org.mapstruct:mapstruct-processor:${property("MAPSTRUCT_VERSION")}")
    kaptTest("org.mapstruct:mapstruct-processor:${property("MAPSTRUCT_VERSION")}")

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("io.kotest:kotest-runner-junit5:${property("KOTEST_VERSION")}")
    testImplementation("io.kotest:kotest-assertions-core:${property("KOTEST_VERSION")}")
    testImplementation("io.kotest:kotest-property:${property("KOTEST_VERSION")}")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("io.mockk:mockk:${property("MOCKK_VERSION")}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.6")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 테스트시 생성자 리플렉션 관련 에러 임시조치
    jvmArgs(
        "--add-opens",
        "java.base/java.net=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
    )
}

tasks.register<Copy>("copyPreCommitHook") {
    description = "Copy pre-commit git hook from the scripts to the .git/hooks"
    group = "git hooks"
    outputs.upToDateWhen { false }
    from("$rootDir/scripts/pre-commit.sh")
    into("$rootDir/.git/hooks/")
    rename("pre-commit.sh", "pre-commit")
    doLast {
        file("$rootDir/.git/hooks/pre-commit").setExecutable(true)
    }
}

tasks.register<Copy>("copyPrePushHook") {
    description = "Copy pre-push git hook from git-hooks to .git/hooks"
    group = "git hooks"
    outputs.upToDateWhen { false }
    from("$rootDir/scripts/pre-push.sh")
    into("$rootDir/.git/hooks/")
    rename("pre-push.sh", "pre-push")
    doLast {
        file("$rootDir/.git/hooks/pre-push").setExecutable(true)
    }
}

tasks.build {
    dependsOn("copyPreCommitHook", "copyPrePushHook")
}
