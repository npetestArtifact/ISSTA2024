package npetest.language;

import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashMap;
import java.util.Map;

public class IdGenerator {
  private IdGenerator() {}

  private static final Map<String, Integer> indices = new HashMap<>();

  public static String createIndexedVariableName(CtTypeReference<?> type, int index) {
    String varName;
    if (type.isArray()) {
      varName = String.format("%s%s", ((CtArrayTypeReference<?>) type).getComponentType().getSimpleName(), "Array");
    } else if (type.isAnonymous()) {
      String[] splits = type.getQualifiedName().split("\\.");
      varName = splits[splits.length - 1];
    } else {
      varName = type.getSimpleName();
    }
    char[] c = varName.toCharArray();
    c[0] = Character.toLowerCase(c[0]);
    return String.format("%s%d", String.valueOf(c), index);
  }

  public static String generateIdentifier(CtTypeReference<?> type) {
    String simpleName = type.getSimpleName();
    int index = indices.getOrDefault(simpleName, 0);
    indices.put(simpleName, index + 1);
    return createIndexedVariableName(type, index);
  }
}
