package npetest.analysis.dynamicanalysis;

import npetest.analysis.npeanalysis.NPEAnalysisManager;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import npetest.language.sequence.TestCase;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MethodTrace {
  private static final Logger logger = LoggerFactory.getLogger(MethodTrace.class);
  private static final MethodTrace instance = new MethodTrace();

  public static MethodTrace getInstance() {
    return instance;
  }

  private final Map<Integer, List<String>> statementIndexToCalledMethods = new HashMap<>();

  private final Map<ExecutableKey, Set<ExecutableKey>> reachableMethodsMap = new HashMap<>();

  private final Set<String> calledMethodsSet = new HashSet<>();

  private final Set<String> globalNPEMethodCoverage = new HashSet<>();

  public void init() {

    return;
  }

  public void updateReachableMethods(TestCase testCase, Map<Integer, List<ExecutableKey>> calledMethods) {
    for (Map.Entry<Integer, List<ExecutableKey>> entry : calledMethods.entrySet()) {
      Integer i = entry.getKey();
      CtStatement statement = testCase.getCtStatements().getStatement(i);
      ExecutableKey executableKey = null;
      if (statement instanceof CtInvocation<?>) {
        executableKey = ExecutableKey.of(((CtInvocation<?>) statement).getExecutable());
      } else if (statement instanceof CtLocalVariable<?>) {
        CtExpression<?> defaultExpression = ((CtLocalVariable<?>) statement).getDefaultExpression();
        if (defaultExpression instanceof CtAbstractInvocation<?>) {
          executableKey = ExecutableKey.of(((CtAbstractInvocation<?>) defaultExpression).getExecutable());
        }
      }

      if (executableKey != null) {
        Set<ExecutableKey> reachableMethods = reachableMethodsMap.computeIfAbsent(executableKey, key -> new HashSet<>());
        reachableMethods.addAll(entry.getValue());
      }
    }
  }

  public Set<ExecutableKey> getReachableMethods(ExecutableKey m) {
    return reachableMethodsMap.getOrDefault(m, new HashSet<>());
  }
  private enum Mode { OBJ, TC;}
  private Mode mode;

  private int currentIndex = -1;

  public void prepareRunningObjectSequence() {
    mode = Mode.OBJ;
    reset();
  }

  public void prepareRunningTestCase() {
    mode = Mode.TC;
    reset();
  }


  public void reset() {
    currentIndex = -1;
    statementIndexToCalledMethods.clear();
    calledMethodsSet.clear();
  }

  @SuppressWarnings("unused")
  public void incrementLine() {
    // logger.debug("INCREMENTLINE");
    currentIndex++;
  }

  @SuppressWarnings("unused")
  public void recordMethodEntry(String methodKey) {
    switch(mode) {
      case OBJ:
        calledMethodsSet.add(methodKey);
        break;
      case TC:
        statementIndexToCalledMethods.computeIfAbsent(currentIndex, i -> new ArrayList<>()).add(methodKey);
        break;
      default:
        break;
    }
  }

  public Map<Integer, List<ExecutableKey>> collectCalledMethods() {
    Map<Integer, List<ExecutableKey>> statementIndexToCalledMethodsWithKey = new HashMap<>();
    for (Map.Entry<Integer, List<String>> entry : statementIndexToCalledMethods.entrySet()) {
      int key = entry.getKey();
      List<String> calledMethods = entry.getValue();
      List<ExecutableKey> calledMethodKeys = new ArrayList<>();
      for (String calledMethod : calledMethods) {
        CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(calledMethod);
        if (method != null) {
          ExecutableKey methodKey = ExecutableKey.of(method);
          calledMethodKeys.add(methodKey);
          updateNPEMethodCoverage(calledMethod);
        }
      }
      statementIndexToCalledMethodsWithKey.put(key, calledMethodKeys);
    }
    return statementIndexToCalledMethodsWithKey;
}

  private void updateNPEMethodCoverage(String methodKey) {
    if (NPEAnalysisManager.getWeight(methodKey) != 0) {
      globalNPEMethodCoverage.add(methodKey);
    }
  }

  public Set<String> getGlobalNPEMethodCoverage() {
    return globalNPEMethodCoverage;
  }

  public Set<ExecutableKey> getCalledMethodsSet() {
    if (!statementIndexToCalledMethods.isEmpty()) {
      return collectCalledMethods().values().stream()
              .flatMap(Collection::stream).collect(Collectors.toSet());
    } else {
      return calledMethodsSet.stream().map(CtModelExt.INSTANCE::getMethodFromKey)
              .filter(Objects::nonNull).map(ExecutableKey::of).collect(Collectors.toSet());
    }
  }
}
