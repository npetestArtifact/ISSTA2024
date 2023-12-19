package npetest.synthesizer.result;

import npetest.language.metadata.ExecutionResult;
import npetest.language.metadata.ExecutionResult.ExecutionSummary;
import npetest.language.sequence.TestCase;

public class CommentUtils {
  private CommentUtils() {
  }

  public static String createExecutionComment(ExecutionResult executionResult) {
    StringBuilder builder = new StringBuilder(executionResult.toString());
    ExecutionSummary summary = executionResult.getSummary();
    Throwable fault = executionResult.getFault();
    String comment;
    switch (summary) {
      case NPE:
      case CRASH:
        if (executionResult.hasStackTrace()) {
          comment = getFaultInspectionComment(fault, summary);
        } else {
          comment = "Fault has empty stack trace\n";
        }
        builder.append(comment);
        return builder.toString();
      default:
        return builder.toString();
    }
  }

  public static String createMetaDataComment(TestCase testCase) {
    return "Timestamp      : " + testCase.getTime() + "s\n" +
            "Synthesis Time : " + String.format("%.3fs%n", testCase.getSynthesisTime());
  }

  public static String getFaultInspectionComment(Throwable fault, ExecutionSummary summary) {
    switch (summary) {
      case NPE:
        return "Duplicated NPE Count: " + Statistics.getNPECount(fault.getStackTrace()[0]) + " times\n";
      case CRASH:
        return "Duplicated Crash Count: " + Statistics.getCrashCount(fault.getClass().getCanonicalName(),
                fault.getStackTrace()[0]) + " times\n";
      default:
        return null;
    }
  }
}
