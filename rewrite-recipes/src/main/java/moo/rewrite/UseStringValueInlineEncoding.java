package moo.rewrite;

import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Replaces the two inline Latin-1 conversion idioms with {@code StringValue}'s API.
 *
 * <p>Declarative YAML can compose recipes but cannot capture and reuse these constructor
 * subexpressions. Keeping this visitor in the build-only recipe project makes the templates executable
 * without adding OpenRewrite to Banteng's runtime.
 */
public final class UseStringValueInlineEncoding extends Recipe {
  private static final String STRING_VALUE_TYPE = "moo.value.MooValue.StringValue";
  private static final String STANDARD_CHARSETS_TYPE = "java.nio.charset.StandardCharsets";

  @Override
  public String getDisplayName() {
    return "Use StringValue Latin-1 encoding";
  }

  @Override
  public String getDescription() {
    return "Replaces inline Latin-1 StringValue encoding and decoding with its owned API.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<ExecutionContext>() {
      private final JavaTemplate encode =
          JavaTemplate.builder("StringValue.of(#{any(java.lang.String)})")
              .contextSensitive()
              .build();
      private final JavaTemplate decode =
          JavaTemplate.builder("#{any(moo.value.MooValue.StringValue)}.text()")
              .contextSensitive()
              .build();

      @Override
      public J visitNewClass(J.NewClass newClass, ExecutionContext context) {
        Expression encodedText = encodedText(newClass);
        if (encodedText != null) {
          maybeRemoveImport(STANDARD_CHARSETS_TYPE);
          return encode.apply(getCursor(), newClass.getCoordinates().replace(), encodedText);
        }

        Expression stringValue = decodedStringValue(newClass);
        if (stringValue != null) {
          maybeRemoveImport(STANDARD_CHARSETS_TYPE);
          return decode.apply(getCursor(), newClass.getCoordinates().replace(), stringValue);
        }

        return super.visitNewClass(newClass, context);
      }
    };
  }

  private static Expression encodedText(J.NewClass newClass) {
    if (!isStringValue(newClass.getType()) || newClass.getArguments().size() != 1) {
      return null;
    }
    Expression argument = newClass.getArguments().getFirst().unwrap();
    if (!(argument instanceof J.MethodInvocation getBytes)
        || !getBytes.getSimpleName().equals("getBytes")
        || getBytes.getSelect() == null
        || !TypeUtils.isString(getBytes.getSelect().getType())
        || !hasLatin1Argument(getBytes.getArguments())) {
      return null;
    }
    return getBytes.getSelect();
  }

  private static Expression decodedStringValue(J.NewClass newClass) {
    if (!TypeUtils.isString(newClass.getType()) || newClass.getArguments().size() != 2) {
      return null;
    }
    Expression bytesExpression = newClass.getArguments().getFirst().unwrap();
    if (!(bytesExpression instanceof J.MethodInvocation bytes)
        || !bytes.getSimpleName().equals("bytes")
        || bytes.getSelect() == null
        || !hasNoArguments(bytes.getArguments())
        || !isStringValue(bytes.getSelect().getType())
        || !isLatin1(newClass.getArguments().get(1))) {
      return null;
    }
    return bytes.getSelect();
  }

  private static boolean hasLatin1Argument(List<Expression> arguments) {
    return arguments.size() == 1 && isLatin1(arguments.getFirst());
  }

  private static boolean hasNoArguments(List<Expression> arguments) {
    return arguments.isEmpty()
        || (arguments.size() == 1 && arguments.getFirst() instanceof J.Empty);
  }

  private static boolean isLatin1(Expression expression) {
    Expression unwrapped = expression.unwrap();
    J.Identifier identifier;
    if (unwrapped instanceof J.FieldAccess fieldAccess) {
      identifier = fieldAccess.getName();
    } else if (unwrapped instanceof J.Identifier directIdentifier) {
      identifier = directIdentifier;
    } else {
      return false;
    }
    JavaType.Variable fieldType = identifier.getFieldType();
    return identifier.getSimpleName().equals("ISO_8859_1")
        && fieldType != null
        && TypeUtils.isOfClassType(fieldType.getOwner(), STANDARD_CHARSETS_TYPE);
  }

  private static boolean isStringValue(JavaType type) {
    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
    return fullyQualified != null
        && fullyQualified.getFullyQualifiedName().replace('$', '.').equals(STRING_VALUE_TYPE);
  }
}
