package npetest.analysis.fault;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FaultAnalysis {
  private FaultAnalysis() {
  }

  public static List<CtTypeReference<?>> getRelatedTypes(StackTraceElement stackTraceElement) {
    List<CtTypeReference<?>> referencedTypes = new ArrayList<>();
    String className = stackTraceElement.getClassName();
    String methodName = stackTraceElement.getMethodName();
    int lineNumber = stackTraceElement.getLineNumber();

    CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);
    if (ctType == null || ctType.isShadow()) {
      return referencedTypes;
    }

    Collection<? extends CtExecutable<?>> executables = methodName.equals("<init>")
            ? ((CtClass<?>) ctType).getConstructors() : ctType.getMethodsByName(methodName);
    CtBlock<?> body = null;
    for (CtExecutable<?> executable : executables) {
      int line = executable.getPosition().getLine();
      int endLine = executable.getPosition().getEndLine();
      if (lineNumber < line || lineNumber > endLine) {
        continue;
      }

      body = executable.getBody();
    }

    if (body == null) {
      return referencedTypes;
    }

    List<CtStatement> explicitStatements = body.filterChildren(new TypeFilter<>(CtStatement.class))
            .select(stmt -> !stmt.isImplicit()).list();
    for (CtStatement statement : explicitStatements) {
      if (statement.getPosition().getLine() == lineNumber) {
        referencedTypes.addAll(
                statement.getReferencedTypes().stream()
                        .filter(t -> !t.equals(ctType.getReference()) && !TypeUtils.isNull(t))
                        .filter(t -> t.getParent(CtVariableReference.class) != null &&
                                !(t.getParent(CtVariableReference.class).getSimpleName().contains("Message")
                                        || t.getParent(CtVariableReference.class).getSimpleName().contains("message")
                                        || t.getParent(CtVariableReference.class).getSimpleName().contains("Msg")
                                        || t.getParent(CtVariableReference.class).getSimpleName().contains("msg")))
                        .collect(Collectors.toList()));
        break;
      }
    }

    if (referencedTypes.isEmpty()) {
      for (CtStatement statement : explicitStatements) {
        if (statement.getPosition().getLine() <= lineNumber) {
          List<CtTypeReference<?>> candidateRelatedTypes = statement.getReferencedTypes().stream()
                  .filter(t -> !t.equals(ctType.getReference()) && !TypeUtils.isNull(t))
                  .filter(t -> t.getParent(CtVariableReference.class) != null &&
                          !(t.getParent(CtVariableReference.class).getSimpleName().contains("Message")
                                  || t.getParent(CtVariableReference.class).getSimpleName().contains("message")
                                  || t.getParent(CtVariableReference.class).getSimpleName().contains("Msg")
                                  || t.getParent(CtVariableReference.class).getSimpleName().contains("msg")))
                  .collect(Collectors.toList());
          List<CtTypeReference<?>> constructorCallTypes = statement.filterChildren(new TypeFilter<>(CtConstructorCall.class))
                  .map((CtConstructorCall<?> constructorCall) -> constructorCall.getType())
                  .list();
          referencedTypes.addAll(candidateRelatedTypes.stream().filter(t -> !constructorCallTypes.contains(t)).collect(Collectors.toList()));
        } else {
          break;
        }
      }
    }

    return referencedTypes.stream().filter(
            t -> t.getTypeDeclaration() != null && !t.getTypeDeclaration().isShadow()).collect(Collectors.toList());
  }
}
