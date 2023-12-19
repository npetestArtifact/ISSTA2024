package npetest.commons.misc;

import npetest.commons.keys.ExecutableKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Debugger {
  private static final Logger logger = LoggerFactory.getLogger(Debugger.class);

  private static int failCount = 0;

  private static File synthesisLogFile;

  private static File compilationFailureLogFile;

  private static File toolErrorLogFile;

  private static File unexpectedErrorLogFile;

  private static boolean enabled = false;

  public static void setupFiles(File debugPath) throws IOException {
    FileUtils.createFile(debugPath, true, true);
    synthesisLogFile = new File(debugPath, "synthesis.failure.log");
    compilationFailureLogFile = new File(debugPath, "compile.failure.log");
    toolErrorLogFile = new File(debugPath, "tool.error.log");
    unexpectedErrorLogFile = new File(debugPath, "unexpected.log");
  }

  public static void logSynthesisFailure(ExecutableKey mutKey, RuntimeException e) {
    if (!enabled) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("===== Synthesis Failure =====\n")
            .append(Timer.GLOBAL_TIMER.toLogMessageFormat()).append('\n')
            .append("MUT: ").append(mutKey).append("\n")
            .append(e.getMessage()).append("\n\n");
    for (StackTraceElement stackTraceElement : e.getStackTrace()) {
      builder.append("  ").append(stackTraceElement).append('\n');
    }
    Debugger.log(builder.toString(), LogType.SYNTHESIS_FAILURE);
  }

  public static void logToolError(ExecutableKey mutKey, Throwable e) {
    if (!enabled) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("===== Tool Error =====\n")
            .append(Timer.GLOBAL_TIMER.toLogMessageFormat()).append('\n')
            .append("MUT: ").append(mutKey).append("\n")
            .append(e.toString()).append("\n\n");
    for (StackTraceElement stackTraceElement : e.getStackTrace()) {
      builder.append("  ").append(stackTraceElement).append('\n');
    }
    log(builder.toString(), LogType.TOOL_ERROR);
  }

  public static void logCompileFailure(String code, List<? extends Diagnostic<?>> diagnostics) {
    if (!enabled) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder.append("===== Compile Failure =====\n")
            .append(Timer.GLOBAL_TIMER.toLogMessageFormat()).append('\n')
            .append(code).append('\n');
    for (Diagnostic<?> diagnostic : diagnostics) {
      builder.append("  ").append(diagnostic.toString()).append('\n');
    }
    log(builder.toString(), LogType.COMPILE_FAILURE);
  }

  public static void logUnexpectedException(String msg) {
    if (!enabled) {
      return;
    }
    String message = "===== Unexpected Exception =====\n" +
            Timer.GLOBAL_TIMER.toLogMessageFormat() + '\n' +
            "Unexpected behavior in type hierarchy analysis\n" +
            msg;
    log(message, LogType.UNEXPECTED);
  }

  public static void log(String message, LogType logType) {
    File file;
    try {
      file = getLogFile(logType);
    } catch (IOException e) {
      return;
    }

    if (file != null) {
      try (FileWriter fw = new FileWriter(file, true)) {
        fw.write(message + '\n');
      } catch (IOException e) {
        if (failCount == 0) {
          logger.error("Failed to write logging message to file", e);
          failCount++;
        }
      }
    }
  }

  private static File getLogFile(LogType logType) throws IOException {
    File logFile;
    switch (logType) {
      case SYNTHESIS_FAILURE:
        logFile = synthesisLogFile;
        break;
      case COMPILE_FAILURE:
        logFile = compilationFailureLogFile;
        break;
      case TOOL_ERROR:
        logFile = toolErrorLogFile;
        break;
      case UNEXPECTED:
        logFile = unexpectedErrorLogFile;
        break;
      default:
        return null;
    }
    if (!logFile.exists()) {
      FileUtils.createFile(logFile, true, false);
    }
    return logFile;
  }

  public static void enable() {
    enabled = true;
  }

  public enum LogType {
    SYNTHESIS_FAILURE, COMPILE_FAILURE, TOOL_ERROR, UNEXPECTED
  }
}
