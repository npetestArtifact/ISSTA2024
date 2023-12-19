package npetest.analysis.npeanalysis;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

public class NullableGetterInvocationAnalyzer extends MethodAnalyzer {
  private static final NullableGetterInvocationAnalyzer instance = new NullableGetterInvocationAnalyzer();

  public static NullableGetterInvocationAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> nullableGetterCallCounts = new HashMap<>();

  @Override
  public void analyze(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    int nullableGetterCallCount = method.filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> invocation) -> invocation.getExecutable() != null &&
                    invocation.getExecutable().getExecutableDeclaration() != null)
            .select((CtInvocation<?> invocation) -> NullableFieldAnalyzer.getInstance().getNullableFieldGetters()
                    .contains(ExecutableKey.of(invocation.getExecutable().getExecutableDeclaration())))
            .list()
            .size();
    nullableGetterCallCounts.put(methodKey, nullableGetterCallCount);
  }

  public int getScore(String methodKey) {
    return nullableGetterCallCounts.getOrDefault(methodKey, 0);
  }
}
