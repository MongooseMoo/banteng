package world.mongoose.banteng.builtin;

import java.util.List;

/** Appends builtin specifications in the family's declared order. */
public final class BuiltinRegistrar {
  private final List<BuiltinSpec> entries;

  public BuiltinRegistrar(List<BuiltinSpec> entries) {
    this.entries = entries;
  }

  public Registration builtin(String name, List<CallShape> shapes) {
    return new Registration(name, shapes);
  }

  public final class Registration {
    private final String name;
    private final List<CallShape> shapes;
    private BuiltinPermissionRule permission = BuiltinPermissionRule.ANY;
    private boolean irrevocable;

    private Registration(String name, List<CallShape> shapes) {
      this.name = name;
      this.shapes = shapes;
    }

    public Registration permission(BuiltinPermissionRule rule) {
      permission = rule;
      return this;
    }

    public Registration irrevocable() {
      irrevocable = true;
      return this;
    }

    public void implementation(BuiltinHandler handler) {
      entries.add(new BuiltinSpec(name, shapes, permission, irrevocable, handler));
    }
  }
}
