package npetest.synthesizer.context;

import npetest.commons.keys.ExecutableKey;
import spoon.reflect.declaration.CtExecutable;

import java.util.HashSet;
import java.util.Set;

public class GenerationHistory {
  private GenerationHistory() {}
  private static final Set<ExecutableKey> usedGenerators = new HashSet<>();

  private static final Set<String> mutationTargetBlackList = new HashSet<>();

  private static final Set<String> instanceBlackList = new HashSet<>();

  public static boolean isInstantiable(String qualifiedName) {
    return !instanceBlackList.contains(qualifiedName);
  }

  public static void addImmutableType(String qualifiedName) {
    mutationTargetBlackList.add(qualifiedName);
  }

  public static boolean isMutable(String qualifiedName) {
    return !mutationTargetBlackList.contains(qualifiedName);
  }

  public static boolean hasGeneratorBeenChosen(ExecutableKey executableKey) {
    return usedGenerators.contains(executableKey);
  }

  public static void updateUsedGenerators(CtExecutable<?> executable) {
    usedGenerators.add(ExecutableKey.of(executable));
  }
}
