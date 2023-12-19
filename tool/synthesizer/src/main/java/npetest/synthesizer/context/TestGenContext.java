package npetest.synthesizer.context;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.Timer;
import npetest.language.CodeFactory;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.SequenceUtils;
import npetest.language.sequence.TestCase;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGenContext {
  private TestGenContext() {
  }

  private static CtPackage ctPackage;

  private static CtType<?> mainType;

  private static float startTime;

  private static final float TEST_GEN_TIMEOUT = 300f;

  public static final Map<ExecutableKey, Integer> generationCount = new HashMap<>();

  private static TestCase seedTestCase;

  private static final List<TestCase> npeTriggeringObjectSequences = new ArrayList<>();

  public static void startMutation(TestCase testCase) {
    markStart();
    ctPackage = testCase.getCtPackage();
    seedTestCase = testCase;
  }

  public static void startGeneration(CtType<?> mainCtType) {
    markStart();
    mainType = mainCtType;
    ctPackage = mainCtType.getParent(CtPackage.class);
    seedTestCase = null;
  }

  private static void markStart() {
    startTime = Timer.GLOBAL_TIMER.getElapsedTime();
    npeTriggeringObjectSequences.clear();
  }

  public static CtPackage getCtPackage() {
    return ctPackage;
  }

  public static float getStartTime() {
    return startTime;
  }

  public static TestCase getSeedTestCase() {
    return seedTestCase;
  }

  public static boolean testGenerationTimeOut() {
    return Timer.GLOBAL_TIMER.getElapsedTime() - startTime > TEST_GEN_TIMEOUT;
  }

  public static List<TestCase> getInterimNPETriggeringObjectSequences() {
    return npeTriggeringObjectSequences;
  }

  public static void addNPETriggeringObjectSequence(CtStatementList statements, ExecutionResult result) {
    NullPointerException npe = (NullPointerException) result.getFault();
    int testCaseIndex = SequenceUtils.getStatementIndexOfTestCase(npe.getStackTrace());
    List<CtStatement> ctStatements = statements.clone().getStatements().subList(0, testCaseIndex + 1);
    TestCase npeTriggeringTest = new TestCase(
            CodeFactory.createStatementList().setStatements(ctStatements), ctPackage, mainType, startTime);
    npeTriggeringObjectSequences.add(npeTriggeringTest);
  }

  public static CtType<?> getMainType() {
    return mainType;
  }
}
