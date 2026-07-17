import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    application
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

val jcstress = sourceSets.create("jcstress") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val jmh = sourceSets.create("jmh") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[jcstress.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[jcstress.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
configurations[jcstress.annotationProcessorConfigurationName].setExtendsFrom(emptyList())
configurations[jmh.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[jmh.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())
configurations[jmh.annotationProcessorConfigurationName].setExtendsFrom(emptyList())

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

    add(jcstress.implementationConfigurationName, "org.openjdk.jcstress:jcstress-core:0.16")
    add(
        jcstress.annotationProcessorConfigurationName,
        "org.openjdk.jcstress:jcstress-core:0.16",
    )
    add(jmh.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
    add(
        jmh.annotationProcessorConfigurationName,
        "org.openjdk.jmh:jmh-generator-annprocess:1.37",
    )
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

tasks.named<JavaCompile>(jcstress.compileJavaTaskName) {
    // jcstress 0.16 generates harness code that fails Error Prone checks; javac lint remains strict.
    options.errorprone.enabled.set(false)
}

tasks.named<JavaCompile>(jmh.compileJavaTaskName) {
    // JMH 1.37 does not mark generated harness code as generated; javac lint remains strict.
    options.errorprone.enabled.set(false)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    dependsOn(tasks.installDist)
    environment("JAZZER_FUZZ", "0")
    val executableName =
        if (System.getProperty("os.name").startsWith("Windows")) "banteng.bat" else "banteng"
    systemProperty(
        "banteng.executable",
        layout.buildDirectory.file("install/banteng/bin/$executableName").get().asFile.absolutePath,
    )
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

val jcstressJar = tasks.register<Jar>("jcstressJar") {
    archiveClassifier = "jcstress"
    from(sourceSets.main.get().output)
    from(jcstress.output)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<JavaExec>("jcstress") {
    description = "Runs the jcstress publication smoke test"
    group = "verification"
    dependsOn(jcstressJar)
    classpath =
        files(
            jcstressJar.flatMap { it.archiveFile },
            configurations[jcstress.runtimeClasspathConfigurationName],
        )
    mainClass = "org.openjdk.jcstress.Main"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir(layout.buildDirectory.get().asFile)
    args(
        "-t",
        "^moo\\.jcstress\\.VolatilePublicationTest$",
        "-m",
        "quick",
        "-f",
        "1",
        "-r",
        layout.buildDirectory.dir("reports/jcstress").get().asFile.absolutePath,
    )
    outputs.upToDateWhen { false }
}

val jmhJar = tasks.register<Jar>("jmhJar") {
    archiveClassifier = "jmh"
    from(sourceSets.main.get().output)
    from(jmh.output)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<JavaExec>("jmh") {
    description = "Runs the forked JMH parser benchmark"
    group = "verification"
    dependsOn(jmhJar)
    classpath =
        files(
            jmhJar.flatMap { it.archiveFile },
            configurations[jmh.runtimeClasspathConfigurationName],
        )
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    workingDir(layout.buildDirectory.get().asFile)
    args(
        "^moo\\.benchmark\\.ParserBenchmark\\.parse$",
        "-f",
        "1",
        "-wi",
        "1",
        "-i",
        "1",
        "-w",
        "100ms",
        "-r",
        "100ms",
        "-to",
        "5s",
        "-foe",
        "true",
    )
    outputs.upToDateWhen { false }
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.wrapper {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
}
