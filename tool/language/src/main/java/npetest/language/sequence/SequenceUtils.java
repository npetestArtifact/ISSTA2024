package npetest.language.sequence;

import npetest.commons.Configs;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.path.CtRole;

public class SequenceUtils {
  private SequenceUtils() {
  }

  public static int getLineNumberAtGeneratedSequence(StackTraceElement[] stackTrace) {
    int locationInSequence = -1;
    for (StackTraceElement stackTraceElement : stackTrace) {
      String fileName = stackTraceElement.getFileName();
      if (fileName != null && (fileName.startsWith(Configs.RUNTIME_TC_PREFIX)
              || fileName.startsWith(Configs.RUNTIME_OBJ_PREFIX))) {
        locationInSequence = stackTraceElement.getLineNumber();
        break;
      }
    }
    return locationInSequence;
  }


  public static int getFaultTriggeringStatementIndex(TestCase testCase) {
    Throwable fault = testCase.getResult() == null ? null : testCase.getResult().getFault();
    if (fault == null) {
      return -1;
    }
    StackTraceElement[] stackTrace = fault.getStackTrace();
    return stackTrace == null ? -1 : getStatementIndexOfTestCase(stackTrace);
  }

  public static int getStatementIndexOfTestCase(StackTraceElement[] stackTrace) {
    int lineNumberAtGeneratedSequence = getLineNumberAtGeneratedSequence(stackTrace);
    return lineNumberAtGeneratedSequence == -1 ? -1 : lineNumberAtGeneratedSequence - 4;
  }

  public static CtStatement getEnclosingStatement(CtCodeElement codeElement) {
    CtCodeElement result = codeElement;
    while (result != null) {
      if (result instanceof CtStatement && result.getRoleInParent().equals(CtRole.STATEMENT)) {
        return (CtStatement) result;
      }
      result = result.getParent(CtStatement.class);
    }
    return null;
  }
}