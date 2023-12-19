package npetest.synthesizer.result;

import npetest.language.metadata.ExecutionResult;
import npetest.commons.Configs;
import npetest.commons.logger.LogMessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProgressLogger {
  private static final Logger logger = LoggerFactory.getLogger(ProgressLogger.class);

  private ProgressLogger() {
  }

  private static final int LOGGING_PERIOD = 200;

  private static int totalTestCases;

  private static int droppedTestCases;

  private static int savedTestCases;

  private static int previousQuotient = 0;

  public static void print(ExecutionResult result) {
    logger.debug(LogMessageTemplate.TRANSITION_TO, result.getSummary());

    totalTestCases++;
    switch (result.getSummary()) {
      case COMPILE_FAILURE:
      case EXECUTION_FAILURE:
        // drop TC
        droppedTestCases++;
        break;
      case NORMAL_EXECUTION:
      case NPE:
      case CRASH:
        savedTestCases++;
        break;
    }

    if (Configs.DEBUG) {
      if ((totalTestCases % LOGGING_PERIOD) == 0) {
        logger.info("[Progress] Total: {}, Dropped: {}, Saved: {}", totalTestCases, droppedTestCases, savedTestCases);
      }
    } else {
      int quotient = savedTestCases % LOGGING_PERIOD;
      if (quotient != previousQuotient && quotient == 0) {
        logger.info("[Progress] Tests: {}", savedTestCases);
      }
      previousQuotient = quotient;
    }
  }
}
