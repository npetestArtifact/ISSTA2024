package npetest.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import npetest.cli.CLIOptions.InputForm;
import npetest.commons.Configs;
import npetest.commons.exceptions.CLIOptionValidationError;
import spoon.JarLauncher;
import spoon.Launcher;
import spoon.MavenLauncher;

public class LauncherBuilder {
  private LauncherBuilder() {
  }

  static synchronized Launcher createLauncher(InputForm inputForm) {
    if (inputForm.mvnProjectRoot != null) {
      return validateMvnProject(inputForm);
    }

    if (inputForm.jarFile != null) {
      return validateJarArchive(inputForm);
    }

    if (inputForm.javaSourceRoot != null) {
      return validateJavaSourceRoot(inputForm);
    }
    return null;
  }

  private static synchronized Launcher validateMvnProject(InputForm inputForm) {
    if (inputForm.mvnProjectRoot.exists()) {
      try {
        Configs.MAVEN_PROJECT_DIR = inputForm.mvnProjectRoot.getCanonicalPath();
        return new MavenLauncher(inputForm.mvnProjectRoot.getCanonicalPath(), Configs.SOURCE_TYPE);
      } catch (IOException e) {
        throw CLIOptionValidationError.fromIOException(inputForm.mvnProjectRoot, e);
      }
    } else {
      throw CLIOptionValidationError.noSuchFile(inputForm.mvnProjectRoot);
    }
  }

  private static synchronized Launcher validateJarArchive(InputForm inputForm) {
    if (inputForm.jarFile.exists()) {
      Configs.JAR_FILE = inputForm.jarFile;
      return new JarLauncher(Configs.JAR_FILE.getAbsolutePath());
    } else {
      throw CLIOptionValidationError.noSuchFile(inputForm.jarFile);
    }
  }

  private static synchronized Launcher validateJavaSourceRoot(InputForm inputForm) {
    if (inputForm.javaSourceRoot.exists()) {
      try {
        Configs.JAVA_SOURCE_ROOT = inputForm.javaSourceRoot.getCanonicalPath();
        return createJavaSourceRootLauncher(inputForm.javaSourceRoot.getCanonicalPath());
      } catch (IOException e) {
        throw CLIOptionValidationError.fromIOException(inputForm.mvnProjectRoot, e);
      }
    } else {
      throw CLIOptionValidationError.noSuchFile(inputForm.mvnProjectRoot);
    }
  }

  private static synchronized Launcher createJavaSourceRootLauncher(String canonicalPath) {
    Launcher launcher = new Launcher();
    Path dir = Paths.get(canonicalPath);
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.forEach(path -> {
        try {
          addInputResource(launcher, path.toFile());
        } catch (IOException e) {
          throw CLIOptionValidationError.fromIOException(path.toFile(), e);
        }
      });
      return launcher;
    } catch (IOException e) {
      return null;
    }
  }

  private static void addInputResource(Launcher launcher, File file) throws IOException {
    String canonicalPath = file.getCanonicalPath();
    if (canonicalPath.contains("/test/")) {
      return;
    }
    if (!file.isDirectory() && file.getName().endsWith(".java")) {
      launcher.addInputResource(file.getCanonicalPath());
    }
  }
}
