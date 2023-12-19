package npetest.synthesizer.search.method;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.language.sequence.TestCase;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashSet;
import java.util.Set;

public class DefaultModifyingMethodSearcher extends ModifyingMethodSearcher {
  public DefaultModifyingMethodSearcher() {
  }

  public CtMethod<?> select(CtTypeReference<?> instanceType) {
    Set<CtMethod<?>> apis = new HashSet<>(getAccessibleMethods(instanceType));
    return RandomUtils.select(apis);
  }

  @Override
  public void updateScore(TestCase testCase, TestCase result, ExecutableKey method) {
    return;
  }
}
