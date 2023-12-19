package npetest.analysis.npeanalysis;

import java.util.HashMap;
import java.util.Map;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ParameterKey;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

public class NullableParameterAccessAnalyzer extends MethodAnalyzer {
  private static final NullableParameterAccessAnalyzer instance = new NullableParameterAccessAnalyzer();

  public static NullableParameterAccessAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> nullableParameterAccessCounts = new HashMap<>();

  @Override
  public void analyze(String methodKey) {
    // Analysis result should be updated
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    int weight = countNullableParameterAccess(method);
    nullableParameterAccessCounts.put(methodKey, weight);
  }

  // check whether there exists any accesses to nullable parameters
  // if yes, return true
  public boolean hasNullableParamterAccess(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    
    for (int i = 0; i < method.getParameters().size(); i++) {
      CtParameter<?> parameter = method.getParameters().get(i);
      if (!TypeUtils.isPrimitive(parameter.getType().getTypeDeclaration())) {
        if (!ASTUtils.hasVariableAccess(method, parameter)) return true;
      }
    }
    return false;
  }

  private int countNullableParameterAccess(CtMethod<?> method) {
    int count = 0;
    for (int i = 0; i < method.getParameters().size(); i++) {
      CtParameter<?> parameter = method.getParameters().get(i);
      if (NullLiteralAnalyzer.getInstance().getNullableParameters().contains(
              ParameterKey.of(method, i))) {
        count += ASTUtils.countVariableAccess(method, parameter);
      }
    }
    return count;
  }

  public int getScore(String methodKey) {
    return nullableParameterAccessCounts.getOrDefault(methodKey, 0);
  }
}
