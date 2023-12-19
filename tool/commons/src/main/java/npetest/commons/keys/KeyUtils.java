package npetest.commons.keys;

import javassist.CtBehavior;
import javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyUtils {
  private KeyUtils() {}

  private static final Logger logger = LoggerFactory.getLogger(KeyUtils.class);

  public static String ofCtBehavior(CtBehavior ctMethod) {
    try {
      StringBuilder builder = new StringBuilder("(");
      int n = ctMethod.getParameterTypes().length;
      for (int i = 0; i < n; i++) {
        builder.append(ctMethod.getParameterTypes()[i].getName());
        if (i != n - 1) {
          builder.append(",");
        }
      }
      builder.append(')');
      return builder.toString();
    } catch (NotFoundException e) {
      e.printStackTrace();
      logger.error("Failed to get parameter types of method - {}", ctMethod.getLongName());
      return null;
    }
  }
}
