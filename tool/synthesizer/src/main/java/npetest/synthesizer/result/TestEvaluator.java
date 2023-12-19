package npetest.synthesizer.result;

import npetest.analysis.complexityanalysis.ComplexityAnalyzer;
import npetest.analysis.dynamicanalysis.ActualRuntimeType;
import npetest.analysis.dynamicanalysis.DynamicInformation;
import npetest.analysis.dynamicanalysis.MethodTrace;
import npetest.analysis.executor.ExecutionHelper;
import npetest.analysis.npeanalysis.NPEAnalysisManager;
import npetest.commons.keys.ExecutableKey;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.stream.Collectors;

public class TestEvaluator {
  private static final Logger logger = LoggerFactory.getLogger(TestEvaluator.class);

  private TestEvaluator() {
  }

  public static void evaluate(TestCase testCase) {
    logger.debug("* Evaluating Test-{} ...", testCase.getId());
    ExecutionResult executionResult = ExecutionHelper.INSTANCE.runTestCase(testCase.getCtPackage(), testCase);
    ProgressLogger.print(executionResult);
    testCase.setResult(executionResult);

    // DynamicInformation.postProcess();
    analyzeDynamicInformation(testCase);
  }

  public static void analyzeDynamicInformation(TestCase testCase) {
    if (!testCase.isValid()) {
      return;
    }
    recordExecutionInformation(testCase);
    Statistics.updateStatistics(testCase);
    TestEvaluator.recordScores(testCase);
  }

  public static void recordExecutionInformation(TestCase testCase) {
    Map<Integer, List<ExecutableKey>> calledMethods = MethodTrace.getInstance().collectCalledMethods();
    MethodTrace.getInstance().updateReachableMethods(testCase, calledMethods);
    testCase.setMethodTrace(calledMethods);
    testCase.setCreatedTypes(ActualRuntimeType.getInstance().getActualTypes());
  }

  public static void recordScores(TestCase testCase) {
    float score = calculateScore(testCase);
    testCase.setScore(score);
  }

  public static float calculateScore(TestCase testCase) {
    List<ExecutableKey> calledMethods = new ArrayList<>();
    Map<Integer, List<ExecutableKey>> methodTrace = testCase.getMethodTrace();
    
    
    // for (int i = 0; i < testCase.length(); i++) {
    //   List<ExecutableKey> executableKeys = methodTrace.get(i);
    //   if (executableKeys == null) {
    //     continue;
    //   }
    //   calledMethods.addAll(executableKeys);
    // }

    // if (!methodTrace.containsKey(testCase.length() - 1)) return 0.05f;
    if (!methodTrace.containsKey(testCase.length() - 1)) return 0.0f;
    
    float npeLikelyScore = calledMethods.isEmpty() ? 
        calculateNpePathScore(testCase) : calculateNpePathScore(methodTrace.get(testCase.length() - 1));

    return npeLikelyScore;
  }

  public static float calculateNpePathScore(Collection<ExecutableKey> calledMethods) {
    float score = 0;

    for (ExecutableKey calledMethod : calledMethods) {
      score += NPEAnalysisManager.getPathWeight(calledMethod.toString());
    }

    return score;
  }

  public static float calculateNpePathScore(TestCase testCase) {
    float score = 0;
    CtStatementList tmp = testCase.getCtStatements();

    CtStatement targetStmt = tmp.getStatement(tmp.getStatements().size() - 1);
   
    List<CtInvocation<?>> targetMethod = targetStmt.filterChildren(new TypeFilter(CtInvocation.class)).list();

    //TODO: CHECK
    if (targetMethod.isEmpty()) return ((float)1)/((float)10);
    
    score += NPEAnalysisManager.getPathWeight(targetMethod.get(0).getExecutable().getSignature());

    return score;
  }

  public static int calculateNpeMethodCoverage(Collection<ExecutableKey> calledMethods) {
    int score = 0;
    for (ExecutableKey calledMethod : calledMethods) {
      score += NPEAnalysisManager.getWeight(calledMethod.toString());
    }
    return score;
  }

  public static int calculateComplexMethodCoverage(Collection<ExecutableKey> calledMethods) {
    int score = 0;
    Set<ExecutableKey> filteredMethods = calledMethods.stream()
            .filter(e -> !((CtShadowable) e.getCtElement()).isShadow()).collect(Collectors.toSet());
    for (ExecutableKey filteredMethod : filteredMethods) {
      score += ComplexityAnalyzer.getInstance().getScore(filteredMethod.toString());
    }
    return score;
  }
}
