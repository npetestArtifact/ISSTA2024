package npetest.synthesizer.context;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.ParameterKey;
import spoon.reflect.declaration.CtExecutable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class InvocationGenerationContext {
  private static final Deque<ExecutableKey> workingExecutables = new ArrayDeque<>();

  public static final Map<ParameterKey, Integer> parameterTypeSpaces = new HashMap<>();

  public static void add(CtExecutable<?> executable) {
    workingExecutables.add(ExecutableKey.of(executable));
  }

  public static CtExecutable<?> peek() {
    ExecutableKey lastExecutableKey = workingExecutables.peekLast();
    return lastExecutableKey == null ? null : lastExecutableKey.getCtElement();
  }

  public static void remove(CtExecutable<?> executable) {
    workingExecutables.remove(ExecutableKey.of(executable));
  }

  public static boolean contains(CtExecutable<?> executable) {
    return workingExecutables.contains(ExecutableKey.of(executable));
  }

  public static void updateParameterSearchSpace(CtExecutable<?> executable, int i, int count) {
    ParameterKey key = ParameterKey.of(executable, i);
    if (!parameterTypeSpaces.containsKey(key)) {
      parameterTypeSpaces.put(key, count);
    }
  }
}
