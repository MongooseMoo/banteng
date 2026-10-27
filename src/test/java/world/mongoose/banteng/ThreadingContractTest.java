package world.mongoose.banteng;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ThreadingContractTest {
  private static final Path REPOSITORY = Path.of("").toAbsolutePath();

  @Test
  void guardedByIsACompilerEnforcedMainDependency() throws IOException {
    String build = source("build.gradle.kts");
    String locks = source("gradle.lockfile");

    assertTrue(build.contains("error(\"GuardedBy\")"));
    assertTrue(build.contains("com.google.errorprone:error_prone_annotations:2.50.0"));
    assertTrue(
        build.contains(
            "configurations[jcstress.compileOnlyConfigurationName]"
                + ".extendsFrom(configurations.compileOnly.get())"));
    assertTrue(
        locks.contains(
            "com.google.errorprone:error_prone_annotations:2.50.0="
                + "annotationProcessor,compileClasspath,jcstressCompileClasspath,"));
  }

  private static String source(String relativePath) throws IOException {
    return Files.readString(REPOSITORY.resolve(relativePath));
  }
}
