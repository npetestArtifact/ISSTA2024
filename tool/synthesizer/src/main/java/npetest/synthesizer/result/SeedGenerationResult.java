package npetest.synthesizer.result;

import npetest.language.sequence.AbstractSequence;
import npetest.language.sequence.SequenceUtils;
import npetest.language.sequence.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SeedGenerationResult {
  public static final Logger logger = LoggerFactory.getLogger(SeedGenerationResult.class);

  private final List<TestCase> results = new ArrayList<>();

  private final List<TestCase> npeTriggeringObjects = new ArrayList<>();

  public void recordNPETriggeringObject(TestCase npeTriggeringObject) {
    npeTriggeringObjects.add(npeTriggeringObject);
  }

  public void recordTestCase(TestCase testCase) {
    if (!testCase.isValid()) {
      return;
    }

    TestCase result;
    if (testCase.getResult().getFault() != null) {
      result = sliceTestCase(testCase, testCase.getResult().getFault());
    } else {
      result = testCase;
    }
    results.add(result);
  }

  private TestCase sliceTestCase(TestCase testCase, Throwable fault) {
    StackTraceElement[] stackTrace = fault.getStackTrace();
    if (stackTrace == null || stackTrace.length == 0) {
      return testCase;
    }

    int testCaseIndex = SequenceUtils.getStatementIndexOfTestCase(stackTrace);
    return testCase.slice(0, testCaseIndex + 1);
  }

  public List<TestCase> getUniqueResults() {
   Set<AbstractSequence> uniqueTestCases = results.stream()
           .map(AbstractSequence::new).collect(Collectors.toSet());
   return uniqueTestCases.stream().map(AbstractSequence::getTestCase).collect(Collectors.toList());
  }

  public List<TestCase> getNPETriggeringObjects() {
    // return npeTriggeringObjects;
   Set<AbstractSequence> uniqueTestCases = npeTriggeringObjects.stream()
           .map(AbstractSequence::new).collect(Collectors.toSet());
   return uniqueTestCases.stream().map(AbstractSequence::getTestCase).collect(Collectors.toList());
  }
}
