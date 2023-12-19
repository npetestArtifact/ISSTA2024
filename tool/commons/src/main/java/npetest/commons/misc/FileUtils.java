package npetest.commons.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileUtils {
  private FileUtils() {}
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void deleteRecursively(File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File fileInDirectory : files) {
          deleteRecursively(fileInDirectory);
        }
      }
      Files.delete(file.toPath());
    } else {
      Files.delete(file.toPath());
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static boolean createFile(File file, boolean overwrite, boolean isDirectory) throws IOException {
    if (file.exists() && overwrite) {
      deleteRecursively(file);
    }
    if (isDirectory) {
      return file.mkdirs();
    } else {
      try {
        return file.createNewFile();
      } catch (IOException e) {
        return false;
      }
    }
  }
}
