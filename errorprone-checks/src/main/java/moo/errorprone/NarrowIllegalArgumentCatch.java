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

/** Rejects broad try blocks whose {@code IllegalArgumentException} catch can hide other faults. */
@BugPattern(
    summary = "IllegalArgumentException catches must guard exactly one operation",
    severity = BugPattern.SeverityLevel.ERROR)
public final class NarrowIllegalArgumentCatch extends BugChecker
    implements BugChecker.TryTreeMatcher {
  @Override
  public Description matchTry(TryTree tree, VisitorState state) {
    if (!catchesIllegalArgumentException(tree, state)
        || tree.getBlock().getStatements().size() == 1) {
      return Description.NO_MATCH;
    }
    return buildDescription(tree)
        .setMessage("IllegalArgumentException catches must guard exactly one operation")
        .build();
  }

  private static boolean catchesIllegalArgumentException(TryTree tree, VisitorState state) {
    Type illegalArgument = state.getTypeFromString(IllegalArgumentException.class.getName());
    for (CatchTree catchTree : tree.getCatches()) {
      if (catchesIllegalArgumentException(
          catchTree.getParameter().getType(), illegalArgument, state)) {
        return true;
      }
    }
    return false;
  }

  private static boolean catchesIllegalArgumentException(
      Tree caughtTree, Type illegalArgument, VisitorState state) {
    if (caughtTree instanceof UnionTypeTree union) {
      return union.getTypeAlternatives().stream()
          .anyMatch(alternative ->
              catchesIllegalArgumentException(alternative, illegalArgument, state));
    }
    Type caught = ASTHelpers.getType(caughtTree);
    return caught != null && ASTHelpers.isSameType(illegalArgument, caught, state);
  }
}
