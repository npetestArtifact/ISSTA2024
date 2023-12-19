package npetest.cli;

import npetest.analysis.instrument.ClassInstrumentation;
import npetest.commons.Configs;
import npetest.commons.exceptions.CLIOptionValidationError;
import npetest.commons.filters.DereferenceExistenceFilter;
import npetest.commons.filters.SimpleOverloadingExecutableFilter;
import npetest.commons.logger.LoggingConfiguration;
import npetest.commons.misc.Debugger;
import npetest.commons.misc.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import spoon.Launcher;
import spoon.MavenLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class CLIOptions implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(CLIOptions.class);

  protected static class InputForm {

    @Option(names = "--mvn", paramLabel = "MAVEN_DIR", required = true,
            description = "path to the root directory of Maven project")
    public File mvnProjectRoot;

    @Option(names = "--jar", required = true, description = "Jar files where CUTs reside")
    public File jarFile;

    @Option(names = "--java", paramLabel = "ROOT_DIR", required = true,
            description = "path to the project root directory of any project")
    public File javaSourceRoot;
  }


  @ArgGroup(multiplicity = "1")
  protected InputForm inputForm;

  protected static class UUTs {

    @Option(names = "--cuts", split = ",", paramLabel = "CUT", description = "Classes under test")
    public String[] cutNames;

    // method signature contains ',', so it should use other delimiter
    @Option(names = "--muts", split = ":", paramLabel = "MUT", description = "Method under tests")
    public String[] methodKeys;

    @SuppressWarnings("unused")
    @Option(names = "--whole-project", paramLabel = "MUT", description = "Test for whole project")
    public boolean wholeProjectMode;
  }

  @ArgGroup(multiplicity = "1")
  protected UUTs uuts;

  // Time budget
  @Option(names = "--time-budget", paramLabel = "<INT>", defaultValue = "120",
          description = "total time budgets")
  protected int timeBudget;

  // Classpath required to test CUTs
  @Option(names = "--auxiliary-classpath", split = ":", paramLabel = "<STRING>",
          defaultValue = "", description = "for Maven, typically, this is 'target/dependencies/*.jar'")
  protected String[] auxiliaryClasspath;

  @Option(names = "--target-classpath", paramLabel = "<STRING>",
          description = "classpath of compiled project. For Maven, typically, this is 'target/classes'")
  protected String targetClasspath;

  // Java and JUnit version
  @Option(names = "--java-version", paramLabel = "<INT>", defaultValue = "8")
  protected int javaVersion;

  @Option(names = "--auto-Import", paramLabel = "<BOOLEAN>", defaultValue = "false")
  protected boolean autoImport;

  @Option(names = "--junit-version", paramLabel = "<INT>", defaultValue = "4")
  protected int junitVersion;

  // OUTPUT
  @Option(names = "--test-dir", paramLabel = "<DIR>", defaultValue = "npetest",
          description = "the directory where generated complete tests will be located. " +
                  "It'll be prefixed by --output-dir.")
  protected String testDir;

  @Option(names = "--report-dir", paramLabel = "<DIR>", defaultValue = "npetest-report",
          description = "the directory where reports are located. " +
                  "It'll be prefixed by --output-dir.")
  protected String reportDirName;

  @Option(names = "--debug-dir", paramLabel = "<DIR>", defaultValue = "npetest-debug")
  protected String debugDir;

  @Option(names = "--output-dir", paramLabel = "<DIR>",
          description = "the directory where generated files will be saved. "
                  + "For maven project, it is equal to the project root directory")
  protected String outputDir;

  @Option(names = "--write-duplicated-fault", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "write all duplicated faults based on crash location")
  protected boolean writeDuplicatedFault;

  @Option(names = "--tests-per-suite", paramLabel = "<INT>", defaultValue = "0",
          description = "the number of tests per suite")
  protected int testsPerSuite;

  @Option(names = "--print-comment", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "write all duplicated faults based on crash location")
  protected boolean printComment;

  // Additional configuration
  @Option(names = "--seed-selection-strategy", paramLabel = "<TYPE>", defaultValue = "DEFAULT",
          description = "seed selection strategy in mutation phase. " +
                  "possible values: DEFAULT, FEEDBACK")
  protected Configs.SeedSelectionStrategy seedSelectionStrategy;

  @Option(names = "--mut-selection-strategy", paramLabel = "<TYPE>", defaultValue = "DEFAULT",
          description = "mut selection strategy in seed generation. " +
                  "possible values: DEFAULT, FEEDBACK")
  protected Configs.MUTSelectionStrategy mutSelectionStrategy;

  @Option(names = "--mutation-strategy", paramLabel = "<TYPE>", defaultValue = "DEFAULT",
          description = "mutation target selection strategy in mutation-based testing. " +
                  "possible values: DEFAULT, NPE")
  protected Configs.TestCaseMutationStrategy testCaseMutationStrategy;

  @Option(names = "--exclude-test-dirs", paramLabel = "<PATTERN>", split = ",",
          description = "the regex pattern for additional test directory path " +
                  "that will be be excluded.")
  protected String[] excludeTestDirectories;

  @Option(names = "--seed-gen-stopping-condition", paramLabel = "<TYPE>", defaultValue = "TIME",
          description = "stopping condition for test generation. " +
                  "possible values: TIME(default), NPE_METHOD_COVERAGE")
  protected Configs.StoppingConditionType seedGenerationStoppingCondition;

  @Option(names = "--enable-analysis", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "enable static/dynamic analysis to guide synthesis toward NPE-smell")
  protected boolean enableAnalysis;

  @Option(names = "--null-probability", paramLabel = "<FLOAT>", defaultValue = "0.0",
          description = "probability of null for MUT")
  protected float nullProbability;

  @Option(names = "--seed-gen-time-proportion", paramLabel = "<INT>", defaultValue = "2",
          description = "proportion of seed generation time to the whole time budget")
  protected int seedGenTimeProportion;

  @Option(names = "--filter-mut", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "filter method set")
  protected boolean filterMUT;

  // Debugging
  @Option(names = "--debug", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "write debugging into log file")
  boolean debug;

  @Option(names = "--log-level", paramLabel = "<STRING>", defaultValue = "INFO",
          description = "setup logging level. " + " possible values: INFO(default), DEBUG, ERROR")
  String logLevel;

  @Option(names = "--verbose", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "enable verbose logging")
  boolean verbose;

  @Option(names = "--write-exceptions-only", paramLabel = "<BOOLEAN>", defaultValue = "false",
          description = "write tests triggering exceptions only")
  boolean writeExceptionTestsOnly;

  protected Launcher validateCommandLineOptionsAndParameters() throws IOException {
    setOutputDir(inputForm);
    setVersions();
    setMisc();
    setAnalysis();
    return LauncherBuilder.createLauncher(inputForm);
  }

  private void setAnalysis() {
    ClassInstrumentation.enabled = enableAnalysis;
  }

  protected void validateTargetClasspath() throws IOException {
    if (inputForm.mvnProjectRoot != null) {
      targetClasspath = inputForm.mvnProjectRoot.getCanonicalPath() + "/target/classes";
    }

    if (inputForm.jarFile != null) {
      targetClasspath = inputForm.jarFile.getCanonicalPath();
    }

    if (inputForm.javaSourceRoot != null && (targetClasspath == null || !(new File(targetClasspath)).exists())) {
      throw new CLIOptionValidationError("Invalid target classpath for '--java' source root project");

    }
  }

  private void setOutputDir(InputForm inputForm) throws IOException {
    if (outputDir != null) {
      if (Files.exists(Paths.get(outputDir))) {
        Configs.OUTPUT_DIR = outputDir;
        return;
      } else {
        logger.error("Output directory from `--output-dir` option does not exist: {}. Use default one", outputDir);
      }
    }

    if (inputForm.mvnProjectRoot != null) {
      Configs.OUTPUT_DIR = inputForm.mvnProjectRoot.getCanonicalPath();
    }

    if (inputForm.jarFile != null) {
      Configs.OUTPUT_DIR = inputForm.jarFile.getParent();
    }

    if (inputForm.javaSourceRoot != null) {
      Configs.OUTPUT_DIR = inputForm.javaSourceRoot.getCanonicalPath();
    }
  }

  protected synchronized void setVersions() {
    switch (junitVersion) {
      case 4:
        Configs.ANNOTATION = "org.junit.Test";
        break;
      case 5:
        Configs.ANNOTATION = "org.junit.jupiter.api.Test";
        break;
      default:
        throw new IllegalArgumentException("JUnit v" + junitVersion + " is not supported");
    }
  }

  protected synchronized void setMisc() throws IOException {
    // Experiment
    Configs.TIME_BUDGET = timeBudget;
    Configs.SOURCE_TYPE = MavenLauncher.SOURCE_TYPE.APP_SOURCE;
    Configs.NULL_PROBABILITY = nullProbability;
    Configs.PROPORTION_SEED_GENERATION_TIME = seedGenTimeProportion;
    Configs.TEST_CASE_MUTATION_STRATEGY = testCaseMutationStrategy;
    Configs.STOPPING_CONDITION_TYPE = seedGenerationStoppingCondition;
    Configs.SEED_SELECTION_STRATEGY = seedSelectionStrategy;
    Configs.MUT_SELECTION_STRATEGY = mutSelectionStrategy;

    DereferenceExistenceFilter.INSTANCE.set(filterMUT);
    SimpleOverloadingExecutableFilter.INSTANCE.set(filterMUT);

    // Output
    Configs.REPORT_DIR = Configs.OUTPUT_DIR + '/' + reportDirName;
    Configs.TEST_DIRECTORY = Configs.OUTPUT_DIR + '/' + testDir;
    Configs.WRITE_DUPLICATED_FAULT = writeDuplicatedFault;
    Configs.TESTS_PER_SUITE = testsPerSuite;
    Configs.PRINT_COMMENT = printComment;

    File reportDir = new File(Configs.REPORT_DIR);
    FileUtils.createFile(reportDir, true, true);

    Configs.DEBUG = debug;
    if (debug) {
      File debugPath = new File(Configs.OUTPUT_DIR, debugDir);
      Debugger.setupFiles(debugPath);
      Debugger.enable();
    }


    // Logging
    LoggingConfiguration.setDefaultLevel(logLevel);
    LoggingConfiguration.enable();
    Configs.VERBOSE = verbose;


    // Write exception tests only
    Configs.WRITE_EXCEPTION_TESTS_ONLY = writeExceptionTestsOnly;
  }
}
