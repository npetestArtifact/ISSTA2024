package npetest.commons.astmodel;

public class ClassNameUtils {
  private ClassNameUtils() {
  }

  public static boolean isNamedClass(String name) {
    if (name.contains("package-info") || name.contains("module-info") || !name.endsWith(".class")) {
      return false;
    }

    int j = name.lastIndexOf("$");
    if (j == -1) {
      return true;
    }

    int i = name.indexOf(".class");
    String simpleNestedTypeName = name.substring(j + 1, i);
    try {
      Integer.parseInt(simpleNestedTypeName);
      return false;
    } catch (NumberFormatException e) {
      return true;
    }
  }
}
