package npetest.synthesizer.result;

import npetest.commons.Configs;
import npetest.commons.misc.Timer;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.TestCase;

import java.util.*;
import java.util.Map.Entry;

public class Statistics {
  private Statistics() {
  }

  static int totalTests = 0;

  static int normalExecution = 0;

  static int npe = 0;

  static int uniqueNPE = 0;

  static int crash = 0;

  static int uniqueCrash = 0;

  static int executionFailure = 0;

  static final Map<StackTraceElement, List<TestCase>> npeStats = new HashMap<>();

  static final Map<String, Map<StackTraceElement, List<TestCase>>> crashStatss = new HashMap<>();

  public static void updateStatistics(TestCase testCase) {
    ExecutionResult executionResult = testCase.getResult();
    StackTraceElement validLastStackTraceElement = getValidLastStackTraceElement(executionResult);
    totalTests++;
    switch (executionResult.getSummary()) {
      case NORMAL_EXECUTION:
        normalExecution++;
        break;
      case NPE:
        if(updateNPEStatistics(validLastStackTraceElement)) {
          npeStats.get(validLastStackTraceElement).add(testCase);
        }
        break;
      case CRASH:
        String crashName = executionResult.getFault().getClass().getCanonicalName();
        if(updateCrashStatistics(validLastStackTraceElement, crashName)) {
          crashStatss.get(crashName).get(validLastStackTraceElement).add(testCase);
        }
        break;
      case COMPILE_FAILURE:
      case EXECUTION_FAILURE:
        // never occur
        executionFailure++;
        break;
    }
  }

  private static StackTraceElement getValidLastStackTraceElement(ExecutionResult result) {
    if(result.hasStackTrace()) {
      Throwable fault = result.getFault();
      if (fault == null) {
        return null;
      }
      StackTraceElement stackTraceElement = fault.getStackTrace()[0];
      if (stackTraceElement.getFileName() != null &&
              stackTraceElement.getFileName().startsWith(Configs.RUNTIME_TC_PREFIX)) {
        return null;
      }
      return stackTraceElement;
    }
    return null;
  }

  private static boolean updateNPEStatistics(StackTraceElement stackTraceElement) {
    if(stackTraceElement == null) {
      return false;
    }
    npe++;
    List<TestCase> npeTests = npeStats.getOrDefault(stackTraceElement, new ArrayList<>());
    if(npeTests.size() == 0) {
      npeStats.put(stackTraceElement, npeTests);
      uniqueNPE++;
    }
    return true;
  }

  private static boolean updateCrashStatistics(StackTraceElement stackTraceElement, String crashName) {
    if(stackTraceElement == null) {
      return false;
    }
    Map<StackTraceElement, List<TestCase>> crashStats = crashStatss.getOrDefault(crashName, new HashMap<>());
    if(crashStats.size() == 0) {
      crashStatss.put(crashName, crashStats);
    }
    List<TestCase> crashTests = crashStats.getOrDefault(stackTraceElement, new ArrayList<>());
    if (crashTests.size() == 0) {
      crashStats.put(stackTraceElement, crashTests);
      uniqueCrash++;
    }
    return true;
  }

  public static String toStringSummary() {
    /* Summary */
    StringBuilder builder = new StringBuilder();
    builder.append("\n==== Test Generation Result ====\n")
            .append("Elapsed Time : ").append(String.format("%.1fs%n", Timer.GLOBAL_TIMER.getElapsedTime()))
            .append("---------------------------------------\n")
            .append("Total Tests          : ").append(totalTests).append('\n')
            .append("Normal Execution     : ").append(normalExecution).append('\n')
            .append("Total NPE            : ").append(npe).append('\n')
            .append("Unique NPE           : ").append(uniqueNPE).append('\n')
            .append("Total Crash          : ").append(crash).append('\n')
            .append("Unique Crash         : ").append(uniqueCrash).append('\n')
            .append("---------------------------------------\n");

    /* Crash information */
    int i = 0;
    builder.append("Crash details \n\n")
            .append(String.format("%d. java.lang.NullPointerException%n", ++i));
    appendFaultStatistics(builder, npeStats);
    for (Entry<String, Map<StackTraceElement, List<TestCase>>> entry : crashStatss.entrySet()) {
      String crashName = entry.getKey();
      Map<StackTraceElement, List<TestCase>> crashStats = entry.getValue();
      builder.append(String.format("%d. %s%n", ++i, crashName));
      appendFaultStatistics(builder, crashStats);
    }
    return builder.toString();
  }

  private static void appendFaultStatistics(StringBuilder builder, Map<StackTraceElement, List<TestCase>> crashStats) {
    for (Entry<StackTraceElement, List<TestCase>> _entry : crashStats.entrySet()) {
      StackTraceElement faultLocation = _entry.getKey();
      List<TestCase> testCases = _entry.getValue();
      builder.append("  ").append(faultLocation).append(" : ").append(testCases.size()).append('\n');
      for(TestCase tc : testCases) {
        if(tc.getCanonicalPathName() != null) {
          builder.append("    * ").append(tc.getCanonicalPathName()).append('\n');
        }
      }
    }
    builder.append('\n');
  }

  public static int getNPECount(StackTraceElement stackTraceElement) {
    return npeStats.get(stackTraceElement).size();
  }

  public static int getCrashCount(String name, StackTraceElement stackTraceElement) {
    return crashStatss.get(name).get(stackTraceElement).size();
  }

}
