package npetest.commons;

import spoon.MavenLauncher.SOURCE_TYPE;

import java.io.File;

public final class Configs {
  private Configs() {
  }


  public static String MAVEN_PROJECT_DIR;

  public static File JAR_FILE;

  public static String JAVA_SOURCE_ROOT;

  public static String OUTPUT_DIR;

  public static String REPORT_DIR;

  // TEST
  public static String TEST_DIRECTORY;

  public static final String RUNTIME_OBJ_PREFIX = "NPETest_OBJ_";

  public static final String RUNTIME_TC_PREFIX = "NPETest_TC_";

  public static final String SEED_TEST_SUFFIX = "NPETest_Seed";

  public static final String MUTANT_TEST_SUFFIX = "NPETest_Mutant";

  public static final int MAX_ARRAY_LENGTH = 3;

  public static int TESTS_PER_SUITE;

  public static boolean PRINT_COMMENT;
  public enum StoppingConditionType {
    TIME, NPE_METHOD_COVERAGE, NPE_METHOD_BASIC_BLOCK_COVERAGE, FIXED_PARAMETER_SPACE


  }
  public static StoppingConditionType STOPPING_CONDITION_TYPE;

  public static float PROPORTION_SEED_GENERATION_TIME;

  // EXPERIMENT
  public static int TIME_BUDGET;

  public static SOURCE_TYPE SOURCE_TYPE;

  public static boolean WRITE_DUPLICATED_FAULT;

  public static float NULL_PROBABILITY;

  public static final int OBJECT_GENERATION_TRIAL = 3;

  public static final int OBJECT_GENERATION_MUTANT_TRIAL = 10;

  public static float NULL_PROBABILITY_NULLABLE_PARAM = 0.5f;

  public enum SeedSelectionStrategy {
    DEFAULT, FEEDBACK, TEST;
  }
  public static SeedSelectionStrategy SEED_SELECTION_STRATEGY;

  public enum MUTSelectionStrategy {
    DEFAULT, FEEDBACK, TEST;
  }
  public static MUTSelectionStrategy MUT_SELECTION_STRATEGY;

  public enum TestCaseMutationStrategy {
    DEFAULT, FEEDBACK, TEST;
  }
  public static TestCaseMutationStrategy TEST_CASE_MUTATION_STRATEGY;

  // Constant
  public static String ANNOTATION;

  public static boolean DEBUG;

  public static boolean VERBOSE;

  public static boolean WRITE_EXCEPTION_TESTS_ONLY;
}