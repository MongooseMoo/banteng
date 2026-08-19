package world.mongoose.banteng.rewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

/** Retargets references that stock ChangePackage misses for a single-segment package root. */
public final class RetargetSingleSegmentPackageReferences extends Recipe {
  private static final String OLD_PACKAGE = "moo";
  private static final String NEW_PACKAGE = "world.mongoose.banteng";

  @Override
  public String getDisplayName() {
    return "Retarget single-segment package references";
  }

  @Override
  public String getDescription() {
    return "Retargets imports and typed fully-qualified references after ChangePackage moves the "
        + "single-segment moo package root.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<ExecutionContext>() {
      @Override
      public J visitImport(J.Import anImport, ExecutionContext context) {
        J.Import visited = (J.Import) super.visitImport(anImport, context);
        String qualifiedName = qualifiedName(visited.getQualid());
        if (!hasOldPackageRoot(qualifiedName)) {
          return visited;
        }
        J.FieldAccess replacement =
            (J.FieldAccess) TypeTree.build(retargetOldPackageRoot(qualifiedName));
        return visited.withQualid(
            replacement
                .withPrefix(visited.getQualid().getPrefix())
                .withMarkers(visited.getQualid().getMarkers()));
      }

      @Override
      public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext context) {
        J.FieldAccess visited = (J.FieldAccess) super.visitFieldAccess(fieldAccess, context);
        String sourceName = qualifiedName(visited);
        if (!hasOldPackageRoot(sourceName)) {
          return visited;
        }
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
        if (type == null || !isMigratedPackage(type.getPackageName())) {
          return visited;
        }
        String typeName = type.getFullyQualifiedName().replace('$', '.');
        String oldTypeName =
            typeName.startsWith(NEW_PACKAGE + ".")
                ? OLD_PACKAGE + typeName.substring(NEW_PACKAGE.length())
                : typeName;
        if (!sourceName.equals(oldTypeName)) {
          return visited;
        }
        if (hasOldPackageRoot(typeName)) {
          typeName = retargetOldPackageRoot(typeName);
        }
        J replacement = (J) TypeTree.build(typeName);
        return replacement.withPrefix(visited.getPrefix()).withMarkers(visited.getMarkers());
      }
    };
  }

  private static boolean hasOldPackageRoot(String qualifiedName) {
    return qualifiedName.equals(OLD_PACKAGE) || qualifiedName.startsWith(OLD_PACKAGE + ".");
  }

  private static boolean isMigratedPackage(String packageName) {
    return packageName.equals(OLD_PACKAGE)
        || packageName.startsWith(OLD_PACKAGE + ".")
        || packageName.equals(NEW_PACKAGE)
        || packageName.startsWith(NEW_PACKAGE + ".");
  }

  private static String retargetOldPackageRoot(String qualifiedName) {
    return NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
  }

  private static String qualifiedName(Expression expression) {
    if (expression instanceof J.Identifier identifier) {
      return identifier.getSimpleName();
    }
    if (expression instanceof J.FieldAccess access) {
      String target = qualifiedName(access.getTarget());
      return target.isEmpty() ? "" : target + "." + access.getSimpleName();
    }
    return "";
  }
}
