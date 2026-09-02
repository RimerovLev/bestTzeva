plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.3.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tzevabest"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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

val mapstructVersion: String by project
val springdocVersion: String by project
val archunitVersion: String by project
val jjwtVersion: String by project
val awsSdkVersion: String by project
val poiVersion: String by project
val pdfboxVersion: String by project
val logstashVersion: String by project

dependencies {
    // --- Spring Boot starters ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // --- Database ---
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- OpenAPI ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // --- Хранилище медиа (S3 / MinIO) ---
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")

    // --- Экспорт XLSX (складские остатки) ---
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    // --- PDF коммерческих предложений (иврит, встроенный шрифт) ---
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    // --- JWT (jjwt) ---
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // --- Lombok ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // --- MapStruct (after Lombok in annotation-processor chain) ---
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // --- Test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Apache HttpClient5: TestRestTemplate корректно обрабатывает 401 + тело POST
    // (JDK-клиент падает «cannot retry due to server authentication, in streaming mode»).
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:minio")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo() // /actuator/info: версия сборки (T-14.1)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-parameters", // имена параметров для аудит-аспекта (T-13.3)
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.unmappedTargetPolicy=ERROR"
    ))
}

// ── JaCoCo: покрытие (T-14.3) ─────────────────────────────────────────────────
jacoco {
    toolVersion = "0.8.12"
}

// Классы, исключаемые из покрытия: DTO, мапперы, конфигурации, доменные сущности, dev-сидер.
val coverageExclusions = listOf(
    "**/dto/**", "**/mapper/**", "**/config/**", "**/domain/**",
    "**/common/dev/**", "**/TzevaBestApplication*",
    "**/*Scheduler*" // тонкие @Scheduled-обёртки (@Profile !test, в тестах не исполняются)
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude(coverageExclusions) }
    }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) { exclude(coverageExclusions) }
    }))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal() // общий порог
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com.tzevabest.inventory.*", "com.tzevabest.request.*")
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal() // критичные модули
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

