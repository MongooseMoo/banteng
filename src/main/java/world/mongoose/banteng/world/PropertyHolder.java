package world.mongoose.banteng.world;

import java.util.List;

/** Shared property-bearing fields of permanent and anonymous world records. */
public sealed interface PropertyHolder permits WorldObject, WorldAnonymousObject {
  String name();

  int flags();

  long owner();

  List<Long> parents();

  List<WorldVerb> verbs();

  List<WorldProperty> properties();

  /** Property roots which can retain anonymous objects or WAIFs. */
  default List<WorldProperty> collectibleProperties() {
    return ((WorldProperties) properties()).collectibleProperties();
  }
}
