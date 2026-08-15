package moo.builtin;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Shared Toast file-path admission and resolution boundary. */
final class ConfinedFileRoot {
  private final Path root;

  ConfinedFileRoot(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Nullable Path resolve(String name) {
    if (name.startsWith("..") || name.contains("/.")) {
      return null;
    }
    String relative = name.startsWith("/") ? name.substring(1) : name;
    Path resolved = root.resolve(relative).normalize();
    return resolved.startsWith(root) ? resolved : null;
  }
}
