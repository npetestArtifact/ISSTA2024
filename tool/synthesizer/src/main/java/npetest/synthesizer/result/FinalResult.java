package npetest.synthesizer.result;

import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.language.metadata.ExecutionResult;
import npetest.language.metadata.ExecutionResult.ExecutionSummary;
import npetest.language.sequence.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class FinalResult {
  private static final Logger logger = LoggerFactory.getLogger(FinalResult.class);

  private final Set<TestCase> generatedTests = new HashSet<>();

  private final Set<TestCase> finalResults = new HashSet<>();

  private final Map<String, List<TestSuite>> seedTestSuites = new HashMap<>();

  private final Map<String, List<TestSuite>> mutantTestSuites = new HashMap<>();

  private final Map<StackTraceElement, Boolean> duplicateFaultWritten = new HashMap<>();

  public void addTestCases(Collection<TestCase> testCases) {
    generatedTests.addAll(testCases);
  }

  public void report() {
    logger.info("* Creating test suites...");
    gatherTestCasesOfMUT();
    aggregateTestCasesToSuites();
    writeTestSuites(seedTestSuites);
    writeTestSuites(mutantTestSuites);
    writeStatistics();
  }

  private void gatherTestCasesOfMUT() {
    for (TestCase resultTC : generatedTests) {
      addToFinalResults(resultTC);
    }
  }

  private void addToFinalResults(TestCase resultTC) {
    ExecutionResult executionResult = resultTC.getResult();
    ExecutionSummary summary = executionResult.getSummary();
    Throwable fault = executionResult.getFault();
    switch (summary) {
      case NPE:
      case CRASH:
        if (fault.getStackTrace().length == 0) {
          finalResults.add(resultTC);
        } else {
          if (Configs.WRITE_DUPLICATED_FAULT) {
            finalResults.add(resultTC);
          } else {
            String className = fault.getStackTrace()[0].getClassName();
            CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);
            if (ctType == null) {
              return;
            }
            boolean isWritten = duplicateFaultWritten.getOrDefault(fault.getStackTrace()[0], false);
            if (!isWritten) {
              finalResults.add(resultTC);
              duplicateFaultWritten.put(fault.getStackTrace()[0], true);
            }
          }
        }
        break;
      default:
        finalResults.add(resultTC);
    }

  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void writeTestSuites(Map<String, List<TestSuite>> testSuites) {
    for (List<TestSuite> testSuitesList : testSuites.values()) {
      for (TestSuite testSuite : testSuitesList) {
        File file = testSuite.getFile();
        File completeTestSuiteDirectory = new File(file.getParent());
        completeTestSuiteDirectory.mkdirs();
        try (FileWriter out = new FileWriter(file)) {
          testSuite.buildCtCompilationUnit();
          out.append(testSuite.toString());
        } catch (IOException e) {
          logger.error("Failed to dump java file from TestSuite", e);
        }
      }
    }
  }

  private void aggregateTestCasesToSuites() {
    for (TestCase finalResultTC : finalResults) {
      boolean isSeed = finalResultTC.getParent() == null;
      Map<String, List<TestSuite>> testSuites = isSeed ? seedTestSuites : mutantTestSuites;
      CtType<?> mainType = finalResultTC.getMainType();
      CtType<?> topLevelMainClass = mainType.getPosition().getCompilationUnit().getMainType();
      String mainTypeName = topLevelMainClass.getSimpleName();
      List<TestSuite> testSuitesList = testSuites.getOrDefault(mainTypeName, new ArrayList<>());
      TestSuite testSuite;
      if (testSuitesList.isEmpty()) {
        testSuite = new TestSuite(topLevelMainClass, 0, isSeed);
        testSuite.setPackagePath();
        testSuite.setFile();
        testSuitesList.add(testSuite);
      } else {
        testSuite = testSuitesList.get(testSuitesList.size() - 1);
      }

      if (testSuite.size() < Configs.TESTS_PER_SUITE || Configs.TESTS_PER_SUITE == 0) {
        testSuite.addResultTC(finalResultTC);
      } else {
        testSuite = new TestSuite(topLevelMainClass, testSuitesList.size(), isSeed);
        testSuite.setPackagePath();
        testSuite.setFile();
        testSuitesList.add(testSuite);
        testSuite.addResultTC(finalResultTC);
      }

      testSuites.put(mainTypeName, testSuitesList);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void writeStatistics() {
    File resultFile = new File(Configs.REPORT_DIR, "statistics.txt");
    try {
      boolean result = resultFile.createNewFile();
      if (!result) {
        logger.error("* Failed to create statistics file");
      }
    } catch (IOException e) {
      logger.error("* Failed to create statistics file", e);
    }

    String statisticsString = Statistics.toStringSummary();
    logger.info("* Test Generation Result: ");
    for (String s : statisticsString.split("\\n")) {
      logger.info(s);
    }

    try (FileWriter out = new FileWriter(resultFile)) {
      out.write(statisticsString);
    } catch (IOException e) {
      logger.error("* Failed to write statistics to file", e);
    }
  }
}
