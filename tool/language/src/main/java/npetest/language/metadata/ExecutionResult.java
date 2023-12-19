package npetest.language.metadata;

import npetest.commons.Configs;

public class ExecutionResult {
  private final Throwable fault;

  private final ExecutionSummary summary;

  private boolean hasStackTrace = true;

  private ExecutionResult(Throwable fault, ExecutionSummary summary) {
    this.fault = fault;
    this.summary = summary;
  }

  public ExecutionResult(Throwable fault, ExecutionSummary summary, boolean hasStackTrace) {
    this(fault, summary);
    this.hasStackTrace = hasStackTrace;
  }

  public static ExecutionResult ofCompileFailure() {
    return new ExecutionResult(null, ExecutionSummary.COMPILE_FAILURE);
  }

  public static ExecutionResult ofExecutionFailure() {
    return new ExecutionResult(null, ExecutionSummary.EXECUTION_FAILURE);
  }

  public static ExecutionResult ofNormalExecution() {
    return new ExecutionResult(null, ExecutionSummary.NORMAL_EXECUTION);
  }

  public static ExecutionResult fromCrash(Throwable fault) {
    ExecutionSummary summary = fault instanceof NullPointerException ? ExecutionSummary.NPE : ExecutionSummary.CRASH;
    StackTraceElement[] stackTrace = fault.getStackTrace();
    if (stackTrace.length != 0 && stackTrace[0].getFileName() != null) {
      if (stackTrace[0].getFileName().startsWith(Configs.RUNTIME_TC_PREFIX)) {
        // execution failure
        return ExecutionResult.ofExecutionFailure();
      } else {
        return new ExecutionResult(fault, summary);
      }
    } else {
      // fault that is not possible to inspect
      return new ExecutionResult(fault, summary, false);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Execution Result\n");
    builder.append("- Summary: ").append(summary).append('\n');
    switch (summary) {
      case NPE:
      case CRASH:
        assert fault != null;
        if (hasStackTrace) {
          builder.append("- Thrown Exception : ").append(fault).append('\n');
          builder.append("- Stack Trace      : ").append('\n');
          builder.append(summarizeStackTraces(fault)).append('\n');
        } else {
          builder.append("  (The fault stack trace is empty)").append('\n');
        }
        break;
      default:
        break;
    }
    return builder.toString();
  }

  private String summarizeStackTraces(java.lang.Throwable fault) {
    boolean finish = false;
    StringBuilder builder = new StringBuilder();
    for (StackTraceElement stackTraceElement : fault.getStackTrace()) {
      if (finish) {
        break;
      }
      builder.append("    ").append(stackTraceElement).append('\n');
      if (stackTraceElement.getFileName() == null || stackTraceElement.getFileName().startsWith(Configs.RUNTIME_TC_PREFIX)) {
        finish = true;
      }
    }
    return builder.toString();
  }

  public Throwable getFault() {
    return fault;
  }

  public boolean hasStackTrace() {
    return hasStackTrace;
  }

  public boolean isNormalExecution() {
    return summary.equals(ExecutionSummary.NORMAL_EXECUTION);
  }

  public boolean isCompiled() {
    return !summary.equals(ExecutionSummary.COMPILE_FAILURE);
  }

  public boolean isExecutionFailure() {
    return summary.equals(ExecutionSummary.EXECUTION_FAILURE);
  }

  public boolean isNPE() {
    return summary.equals(ExecutionSummary.NPE);
  }

  public enum ExecutionSummary {
    COMPILE_FAILURE, EXECUTION_FAILURE, NORMAL_EXECUTION, NPE, CRASH
  }

  public ExecutionSummary getSummary() {
    return summary;
  }
}
