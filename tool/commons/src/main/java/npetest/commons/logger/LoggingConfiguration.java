package npetest.commons.logger;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingConfiguration {
  private LoggingConfiguration() {
  }

  private static String defaultLevel;

  public static void enable() {
    setLogLevel(defaultLevel);
  }

  public static void disable() {
    setLogLevel("OFF");
  }

  public static void setLogLevel(String level) {
    Level logLevel = Level.valueOf(level);
    ch.qos.logback.classic.Logger rootLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.setLevel(logLevel);
  }

  public static void setDefaultLevel(String defaultLevel) {
    LoggingConfiguration.defaultLevel = defaultLevel;
  }

  public static String getDefaultLevel() {
    return defaultLevel;
  }

  public static boolean isLogLevelDebug() {
    return getDefaultLevel().equals("DEBUG");
  }
}
