package moo.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.tools.javac.code.Type;

/** Rejects broad argument catches and keeps {@code Throwable} out of builtin boundaries. */
@BugPattern(
    summary = "Builtin Throwable catches are forbidden and argument catches must be narrow",
    severity = BugPattern.SeverityLevel.ERROR)
public final class NarrowIllegalArgumentCatch extends BugChecker
    implements BugChecker.TryTreeMatcher {
  @Override
  public Description matchTry(TryTree tree, VisitorState state) {
    if (isBuiltinPackage(state) && catches(tree, Throwable.class, state)) {
      return buildDescription(tree)
          .setMessage("Throwable catches are forbidden in moo.builtin")
          .build();
    }
    if (!catchesIllegalArgumentException(tree, state)
        || tree.getBlock().getStatements().size() == 1) {
      return Description.NO_MATCH;
    }
    return buildDescription(tree)
        .setMessage("IllegalArgumentException catches must guard exactly one operation")
        .build();
  }

  private static boolean catchesIllegalArgumentException(TryTree tree, VisitorState state) {
    return catches(tree, IllegalArgumentException.class, state);
  }

  private static boolean catches(
      TryTree tree, Class<? extends Throwable> exceptionType, VisitorState state) {
    Type expected = state.getTypeFromString(exceptionType.getName());
    for (CatchTree catchTree : tree.getCatches()) {
      if (catches(catchTree.getParameter().getType(), expected, state)) {
        return true;
      }
    }
    return false;
  }

  private static boolean catches(Tree caughtTree, Type expected, VisitorState state) {
    if (caughtTree instanceof UnionTypeTree union) {
      return union.getTypeAlternatives().stream()
          .anyMatch(alternative -> catches(alternative, expected, state));
    }
    Type caught = ASTHelpers.getType(caughtTree);
    return caught != null && ASTHelpers.isSameType(expected, caught, state);
  }

  private static boolean isBuiltinPackage(VisitorState state) {
    Tree packageName = state.getPath().getCompilationUnit().getPackageName();
    if (packageName == null) {
      return false;
    }
    String qualifiedName = packageName.toString();
    return qualifiedName.equals("moo.builtin") || qualifiedName.startsWith("moo.builtin.");
  }
}
