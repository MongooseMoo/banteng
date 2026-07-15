import com.diffplug.spotless.LineEnding
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    application
    id("com.diffplug.spotless") version "8.8.0"
    id("net.ltgt.errorprone") version "5.1.0"
}

group = "moo"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "moo.app.Banteng"
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.jspecify:jspecify:1.0.0")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone("com.uber.nullaway:nullaway:0.13.7")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
    testImplementation("org.jetbrains:jetCheck:0.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        error("NullAway", "RequireExplicitNullMarking")
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    environment("JAZZER_FUZZ", "0")
}

tasks.register<Test>("fuzzTest") {
    description = "Runs the bounded coverage-guided Jazzer target"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("moo.syntax.MooParserFuzzTest.parsesArbitraryLatin1")
    }
    environment("JAZZER_FUZZ", "1")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        googleJavaFormat("1.35.0")
        importOrder()
        removeUnusedImports()
        forbidWildcardImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("buildFiles") {
        target("*.gradle.kts", "gradle.properties", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.check {
    dependsOn(tasks.spotlessCheck)
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
}
