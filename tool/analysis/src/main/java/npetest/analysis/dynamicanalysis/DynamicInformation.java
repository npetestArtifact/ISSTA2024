package npetest.analysis.dynamicanalysis;

/**
 * This class is not directly used by a tool.
 * Rather, the methods defined in this class are
 * inserted to the classes of the subject program
 * through javassist instrumentation API.
 */
public class DynamicInformation {
  private DynamicInformation() {
  }

  public static void prepareRunningObjectSequence() {
    MethodTrace.getInstance().prepareRunningObjectSequence();
    // BasicBlockCoverage.getInstance().disable();
    ActualRuntimeType.getInstance().disable();
  }

  public static void prepareRunningTestCase() {
    MethodTrace.getInstance().prepareRunningTestCase();
    ActualRuntimeType.getInstance().enable();
    // BasicBlockCoverage.getInstance().enable();
  }


  public static void reset() {
    MethodTrace.getInstance().reset();
    ActualRuntimeType.getInstance().reset();
    // BasicBlockCoverage.getInstance().reset();
  }

  public static void postProcess() {
    // BasicBlockCoverage.getInstance().updateBasicBlockCoverage();
  }
}
