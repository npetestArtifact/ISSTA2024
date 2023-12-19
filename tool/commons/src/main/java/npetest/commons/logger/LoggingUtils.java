package npetest.commons.logger;

import npetest.commons.Configs;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LoggingUtils {
  private LoggingUtils() {
  }

  private static final int MAX_NUMBER_TO_LOG_FOR_LIST = 5;

  public static void logResolve(Logger logger, List<?> from, List<?> to) {
    if (!LoggingConfiguration.isLogLevelDebug()) {
      return;
    }
    for (int i = 0; i < from.size(); i++) {
      String src = from.get(i).toString();
      String dst = to.get(i).toString();
      logger.debug(LogMessageTemplate.TRANSITION_FROM_TO, src, dst);
    }
  }

  public static void logList(Logger logger, Collection<?> list, String listName) {
    logger.info("  # {} : {}", listName, list.size());
    int i = 0;
    for (Object element : list) {
      if (Configs.VERBOSE || i < MAX_NUMBER_TO_LOG_FOR_LIST) {
        logger.info("  - {}", element);
      } else {
        logger.info("  - ... ({} more)", list.size() - i);
        break;
      }
      i++;
    }
  }

  public static void logListDebug(Logger logger, Collection<?> list, String listName) {
    if (!LoggingConfiguration.isLogLevelDebug()) {
      return;
    }
    logList(logger, list, listName);
  }

  public static <E> void logListDebug(Logger logger, Collection<E> list, String listName, Function<E, String> toString) {
    if (!LoggingConfiguration.isLogLevelDebug()) {
      return;
    }
    List<String> stringList = list.stream().map(toString).collect(Collectors.toList());
    logList(logger, stringList, listName);
  }

  public static void logFinishTask(Logger logger) {
    logger.info("  - Finished!");
  }
}
