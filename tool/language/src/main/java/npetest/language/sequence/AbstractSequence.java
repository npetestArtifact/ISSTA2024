package npetest.language.sequence;

import npetest.commons.keys.ExecutableKey;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AbstractSequence {

    private static class Pair {
      ExecutableKey executableKey;
      List<CtTypeReference<?>> typeReference;

      public Pair(ExecutableKey executableKey, List<CtTypeReference<?>> typeReference) {
        this.executableKey = executableKey;
        this.typeReference = typeReference;
      }

      @Override
      public int hashCode() {
        int result = executableKey.hashCode();
        if (typeReference == null) {
          return result;
        }

        for (CtTypeReference<?> typeReference : typeReference) {
          result = 31 * result + typeReference.hashCode();
        }
        return result;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair that = (Pair) o;
        if (executableKey.equals(that.executableKey)) {
          if (executableKey.getCtElement() instanceof CtMethod<?>) {
            return true;
          } else {
            return typeReference.equals(that.typeReference);
          }
        }
        return false;
      }
    }

    private final List<Pair> methodSequence = new ArrayList<>();

    private final TestCase testCase;

    public AbstractSequence(TestCase testCase) {
      this.testCase = testCase;

      int length = testCase.getCtStatements().getStatements().size();
      for (int i = 0; i < length; i++) {
        CtStatement statement = testCase.getCtStatements().getStatement(i);
        Pair pair = convert(statement);
        if (pair != null) {
          methodSequence.add(pair);
        }
      }
    }

    private Pair convert(CtStatement statement) {
      if (statement instanceof CtLocalVariable<?>) {
        CtExpression<?> defaultExpression = ((CtLocalVariable<?>) statement).getDefaultExpression();
        if (defaultExpression instanceof CtConstructorCall<?>) {
          CtExecutableReference<?> executable = ((CtConstructorCall<?>) defaultExpression).getExecutable();
          return executable == null ? null : new Pair(ExecutableKey.of(executable), ((CtConstructorCall<?>) defaultExpression).getType().getActualTypeArguments());
        } else if (defaultExpression instanceof CtInvocation<?>) {
          CtExecutableReference<?> executable = ((CtInvocation<?>) defaultExpression).getExecutable();
          return executable == null ? null : new Pair(ExecutableKey.of(executable), null);
        }
      } else if (statement instanceof CtConstructorCall<?>) {
        CtExecutableReference<?> executable = ((CtConstructorCall<?>) statement).getExecutable();
        return executable == null ? null : new Pair(ExecutableKey.of(executable), ((CtConstructorCall<?>) statement).getType().getActualTypeArguments());
      } else if (statement instanceof CtInvocation<?>) {
        CtExecutableReference<?> executable = ((CtInvocation<?>) statement).getExecutable();
        return executable == null ? null : new Pair(ExecutableKey.of(executable), null);
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AbstractSequence that = (AbstractSequence) o;
      return methodSequence.equals(that.methodSequence);
    }

    @Override
    public int hashCode() {
      return Objects.hash(methodSequence);
    }

    public TestCase getTestCase() {
      return testCase;
    }
}
