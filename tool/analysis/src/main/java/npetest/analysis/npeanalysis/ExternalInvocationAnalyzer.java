package npetest.analysis.npeanalysis;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExternalInvocationAnalyzer extends MethodAnalyzer {
  private static final ExternalInvocationAnalyzer instance = new ExternalInvocationAnalyzer();

  public static ExternalInvocationAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> externalCallCounts = new HashMap<>();

  @Override
  public void analyze(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null || externalCallCounts.containsKey(methodKey)) {
      return;
    }

    int weight = externalCallCounts.getOrDefault(methodKey, 0);
    List<CtInvocation<?>> externalMethodCalls = method.filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> invocation) -> invocation.getExecutable() != null &&
                    invocation.getExecutable().getExecutableDeclaration() != null &&
                    invocation.getExecutable().getExecutableDeclaration() instanceof CtShadowable)
            .select((CtInvocation<?> invocation) -> (
                    (CtShadowable) invocation.getExecutable().getExecutableDeclaration()).isShadow())
            .list();
    for (CtInvocation<?> invocation : externalMethodCalls) {
      weight += handleExternalInvocation(method, invocation);
    }
    externalCallCounts.put(methodKey, weight);
  }

  private int handleExternalInvocation(CtMethod<?> method, CtInvocation<?> invocation) {
    if (invocation.getExecutable().getType() != null && TypeUtils.isPrimitive(invocation.getExecutable().getType())) {
      return 0;
    }

    int weight = 0;
    CtRole roleInParent = invocation.getRoleInParent();
    if (roleInParent.equals(CtRole.DEFAULT_EXPRESSION) &&
            invocation.getParent() instanceof CtLocalVariable<?>) {
      CtLocalVariable<?> localVariable = (CtLocalVariable<?>) invocation.getParent();
      weight += ASTUtils.countVariableAccess(method, localVariable);
    } else if (roleInParent.equals(CtRole.ELSE)) {
      if (invocation.getParent().getRoleInParent().equals(CtRole.DEFAULT_EXPRESSION)) {
        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) invocation.getParent().getParent();
        weight += ASTUtils.countVariableAccess(method, localVariable);
      }
    } else if (roleInParent.equals(CtRole.TARGET) || roleInParent.equals(CtRole.ARGUMENT)) {
      weight += 1;
    }
    return weight;
  }

  public int getScore(String methodKey) {
    return externalCallCounts.getOrDefault(methodKey, 0);
  }
}
