package npetest.analysis.complexityanalysis;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtForEachImpl;
import spoon.support.reflect.code.CtForImpl;
import spoon.support.reflect.code.CtIfImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexityAnalyzer extends MethodAnalyzer {
  private static final ComplexityAnalyzer instance = new ComplexityAnalyzer();

  private ComplexityAnalyzer() {
  }

  public static ComplexityAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> complexityScores = new HashMap<>();

  @Override
  public void analyze(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null || complexityScores.containsKey(methodKey)) {
      return;
    }
    CtBlock<?> methodBody = method.getBody();
//    SourcePosition position = methodBody.getPosition();
//    int size = position.getEndLine() - position.getLine();
    List<CtIfImpl> ifConstructs = methodBody.filterChildren(new TypeFilter<>(CtIfImpl.class)).list();
    List<CtForImpl> forConstructs = methodBody.filterChildren(new TypeFilter<>(CtForImpl.class)).list();
    List<CtForEachImpl> forEachConstructs = methodBody.filterChildren(new TypeFilter<>(CtForEachImpl.class)).list();
    List<CtInvocation<?>> recursiveCalls = methodBody.filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> inv) -> inv.getExecutable() != null &&
                    ExecutableKey.of(inv.getExecutable()).toString().equals(methodKey))
            .list();
    int score = (ifConstructs.size() + forConstructs.size() + forEachConstructs.size() + recursiveCalls.size());
    complexityScores.put(methodKey, score);
  }

  public int getScore(String methodKey) {
    return complexityScores.getOrDefault(methodKey, 0);
  }
}
