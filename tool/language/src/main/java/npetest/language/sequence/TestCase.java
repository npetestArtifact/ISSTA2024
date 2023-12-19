package npetest.language.sequence;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.Timer;
import npetest.commons.spoon.ASTUtils;
import npetest.language.CodeFactory;
import npetest.language.metadata.ExecutionResult;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestCase {
  private static final Logger logger = LoggerFactory.getLogger(TestCase.class);
  private static final AtomicInteger idGen = new AtomicInteger();

  private final int id;

  private final CtStatementList statements;

  private final CtPackage ctPackage;

  private final CtType<?> mainType;

  private float score;

  private final Map<Integer, List<ExecutableKey>> methodTrace = new HashMap<>();

  private final Map<String, CtType<?>> createdTypes = new HashMap<>();

  // Mutation score per object
  private final Map<String, Float> mutationScoreMap = new HashMap<>();

  // Mutation tirals per object
  private final Map<String, Integer> mutationTrialMap = new HashMap<>();

  private String mutatedVariable = "";

  // Created time
  private float time;

  // Time consumed in synthesis
  private float synthesisTime;

  private ExecutionResult result;

  private final TestCase parent;

  private int objectMutationCount;

  private String canonicalPathName;

  private int positive;
  private int negative;

  public TestCase(CtStatementList statements, CtPackage ctPackage, CtType<?> mainType, float startTime) {
    this(null, statements, ctPackage, mainType, startTime);
  }

  public TestCase(TestCase parent, CtStatementList statements,
                  CtPackage ctPackage, CtType<?> mainType, float startTime) {
    ASTUtils.removeDuplicatedVariable(statements);
    setMutationScore(statements);
    setMutationTrial(statements);
    this.parent = parent;
    this.statements = statements;
    this.ctPackage = ctPackage;
    this.mainType = mainType;
    this.id = idGen.getAndIncrement();
    this.time = Timer.GLOBAL_TIMER.getElapsedTime();
    this.synthesisTime = time - startTime;
  }

  public CtPackage getCtPackage() {
    return ctPackage;
  }

  public CtType<?> getMainType() {
    return mainType;
  }

  public float getScore() {
    return score;
  }

  public void setScore(float score) {
    this.score = score;
  }

  public CtStatementList getCtStatements() {
    return statements;
  }

  public void setCreatedTypes(Map<String, CtType<?>> createdTypes) {
    this.createdTypes.putAll(createdTypes);
  }

  public void setMethodTrace(Map<Integer, List<ExecutableKey>> methodTrace) {
    this.methodTrace.putAll(methodTrace);
  }

  public int length() {
    return statements.getStatements().size();
  }

  public Map<Integer, List<ExecutableKey>> getMethodTrace() {
    return methodTrace;
  }

  public Set<ExecutableKey> getMethodTraceSet() {
    return methodTrace.values().stream().flatMap(List::stream).collect(Collectors.toSet());
  }

  public Map<String, CtType<?>> getCreatedTypes() {
    return createdTypes;
  }

  public ExecutionResult getResult() {
    return result;
  }

  public void setResult(ExecutionResult result) {
    this.result = result;
  }

  public int indexOf(CtStatement ctStatement) {
    return statements.getStatements().indexOf(ctStatement);
  }

  public float getTime() {
    return time;
  }

  public float getSynthesisTime() {
    return synthesisTime;
  }

  public TestCase getParent() {
    return parent;
  }

  public int getId() {
    return id;
  }

  public boolean isValid() {
    return result.isCompiled() && !result.isExecutionFailure();
  }

  public TestCase slice(int begin, int end) {
    if (end - begin == length()) {
      return this;
    }
    List<CtStatement> ctStatements = statements.clone().getStatements().subList(begin, end);
    TestCase slicedTestCase = new TestCase(CodeFactory.createStatementList().setStatements(ctStatements),
            ctPackage, mainType, -1);
    slicedTestCase.time = time;
    slicedTestCase.synthesisTime = synthesisTime;
    slicedTestCase.result = result;
    for (Entry<Integer, List<ExecutableKey>> entry : methodTrace.entrySet()) {
      int i = entry.getKey();
      List<ExecutableKey> calledMethods = entry.getValue();
      if (i < end) {
        slicedTestCase.methodTrace.put(i, calledMethods);
      }
    }
    ctStatements.stream()
            .filter(stmt -> stmt instanceof CtLocalVariable<?>).map(stmt -> (CtLocalVariable<?>) stmt)
            .forEach(lv -> slicedTestCase.createdTypes.put(lv.getSimpleName(), createdTypes.get(lv.getSimpleName())));
    return slicedTestCase;
  }

  public int getObjectMutationCount() {
    return objectMutationCount;
  }

  public void setObjectMutationCount(int objectMutationCount) {
    this.objectMutationCount = objectMutationCount;
  }

  public void setCanonicalPathName(String canonicalPathName) {
    this.canonicalPathName = canonicalPathName;
  }

  public String getCanonicalPathName() {
    return canonicalPathName;
  }

  public Map<String, Float> getMutationScoreMap() {
    return mutationScoreMap;
  }

  public float getMutationScore(String variableName) {
    return mutationScoreMap.getOrDefault(variableName, -1f);
  }

  public void updateMutationScore(String variableName, boolean flag, float diff) {
    float tmp = mutationScoreMap.getOrDefault(variableName, 0f);
    if (flag) mutationScoreMap.put(variableName, tmp + diff);
    else if (tmp > 0) {
      mutationScoreMap.put(variableName, tmp - 0.1f);
    }
  }

  private void setMutationScore(CtStatementList statements) {
    for (CtStatement statement : statements) {
      if (statement instanceof CtLocalVariable<?>) {
        mutationScoreMap.put(((CtLocalVariable<?>) statement).getSimpleName(), 1f);
      }
    }
  }

  public Map<String, Integer> getMutationTrialMap() {
    return mutationTrialMap;
  }

  public int getMutationTrial(String variableName) {
    return mutationTrialMap.getOrDefault(variableName, 0);
  }

  public void resetMutationTrial(String variableName) {
    mutationTrialMap.put(variableName, 0);
  }

  public void updateMutationTrial(String variableName) {
    int tmp = mutationTrialMap.getOrDefault(variableName, 0);
    mutationTrialMap.put(variableName, tmp + 1);
  }

  public void checkUpdate(boolean flag, TestCase seedCase, TestCase mutant) {
    // logger.info("UPDATES! OBJECT SCORE: " + Boolean.toString(flag));
    float diff = mutant.getMutationScore(mutatedVariable) - seedCase.getMutationScore(mutatedVariable);
    if (diff > 1.0f) diff = 1.0f;
    if (flag) {
      updateMutationScore(mutatedVariable, true, diff);
      resetMutationTrial(mutatedVariable);
      positive++;
    } else if (getMutationTrial(mutatedVariable) > 5) {
      updateMutationScore(mutatedVariable, false, diff);
      resetMutationTrial(mutatedVariable);
      negative++;
    }
  }

  public void printChanges() {
    logger.info("# Positive: " + Integer.toString(positive));
    logger.info("# Negative: " + Integer.toString(negative));
  }

  private void setMutationTrial(CtStatementList statements) {
    for (CtStatement statement : statements) {
      if (statement instanceof CtLocalVariable<?>) {
        mutationTrialMap.put(((CtLocalVariable<?>) statement).getSimpleName(), 0);
      }
    }
  }

  public String getMutatedVar() {
    return mutatedVariable;
  }

  public void setMutatedVar(String variableName) {
    mutatedVariable = variableName;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(id);
  }

  public CtLocalVariable<?> findObject(String variableName) {
    for (CtStatement statement : statements) {
      if (statement instanceof CtLocalVariable<?>
        && (((CtLocalVariable<?>) statement).getSimpleName().equals(variableName)))
      return (CtLocalVariable<?>) statement;
    }
    return null;
  }
}
