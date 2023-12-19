package npetest.synthesizer.search.method;

import npetest.analysis.npeanalysis.PathAnalyzer;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.misc.WeightedCollection;
import npetest.language.sequence.TestCase;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;
import java.util.stream.Collectors;

public class GuidedModifyingMethodSearcher extends ModifyingMethodSearcher {
  private final Map<ExecutableKey, Float> methodScores = new HashMap<>();

  private final Map<ExecutableKey, Integer> selectedCounts = new HashMap<>();

  private final Set<ExecutableKey> selectionHistory = new HashSet<>();

  private float impurityScore(CtTypeReference<?> instanceType, CtMethod<?> targetMethod) {
    String className = targetMethod.getTopLevelType().getQualifiedName();

    String methodKey = className + "#" + targetMethod.getSignature();

    return PathAnalyzer.getInstance().getImpureScore(methodKey);
  }

  
  private float targetRelatedScore(CtTypeReference<?> instanceType, CtMethod<?> targetMethod) {
    float score = 0;
    String className = targetMethod.getTopLevelType().getQualifiedName();

    String methodKey = className + "#" + targetMethod.getSignature();

    Set<String> impureMethods = PathAnalyzer.getInstance().getImpureMethods(methodKey);

    if (!impureMethods.isEmpty()) {

    }

    return score;
  }


  @Override
  public CtMethod<?> select(CtTypeReference<?> instanceType) {
    List<CtMethod<?>> modifyingMethods = getAccessibleMethods(instanceType);

    Map<ExecutableKey, Float> scoreMap = new HashMap<>();

    for (CtMethod<?> modifyingMethod : modifyingMethods) {
      ExecutableKey key = ExecutableKey.of(modifyingMethod);
      float score = methodScores.computeIfAbsent(key, k -> 0f);
      score += impurityScore(instanceType, modifyingMethod);
      scoreMap.put(key, score);
    }



    List<CtMethod<?>> unselectedMethods = modifyingMethods.stream()
            .filter(m -> !selectionHistory.contains(ExecutableKey.of(m))).collect(Collectors.toList());

    if (!unselectedMethods.isEmpty()) {
      CtMethod<?> newMethod = RandomUtils.select(unselectedMethods);
      selectionHistory.add(ExecutableKey.of(newMethod));
      return newMethod;
    }

    // normalize
    float min = scoreMap.values().stream().min(Float::compare).orElse(0f);
    for (Map.Entry<ExecutableKey, Float> entry : scoreMap.entrySet()) {
      ExecutableKey key = entry.getKey();
      Float value = entry.getValue();
      scoreMap.put(key, value - min);
    }

    WeightedCollection<ExecutableKey> rc = new WeightedCollection<>();
    for (Map.Entry<ExecutableKey, Float> entry : scoreMap.entrySet()) {
      ExecutableKey key = entry.getKey();
      Float value = entry.getValue();
      rc.add(value, key);
    }
    ExecutableKey selectedMethodKey = rc.next();
    selectionHistory.add(selectedMethodKey);
    return (CtMethod<?>) selectedMethodKey.getCtElement();
  }

  @Override
  public void updateScore(TestCase testCase, TestCase result, ExecutableKey selectedMutationMethod) {
    Integer count = selectedCounts.getOrDefault(selectedMutationMethod, 0);
    float oldScore = testCase.getScore();
    float mutantScore = result.getScore();
    float diff = mutantScore > oldScore ? mutantScore - oldScore : 0;
    float score = methodScores.getOrDefault(selectedMutationMethod, 0f);
    float newScore = (score * (count + 1) + diff) / (count + 1);
    selectedCounts.put(selectedMutationMethod, count + 1);
    methodScores.put(selectedMutationMethod, newScore);
  }
}
