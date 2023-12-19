package npetest.analysis.npeanalysis;

@SuppressWarnings("unused")
public class NPEAnalysisManager {
  public static void runAnalyzers(String methodKey) {
    NullLiteralAnalyzer.getInstance().analyze(methodKey);
    NullableFieldAccessAnalyzer.getInstance().analyze(methodKey);
    ExternalInvocationAnalyzer.getInstance().analyze(methodKey);
    NullableGetterInvocationAnalyzer.getInstance().analyze(methodKey);
    NullableParameterAccessAnalyzer.getInstance().analyze(methodKey);
  }

  public static void runImpurityAnalyzer(String methodKey) {
    ImpurityAnalyzer.getInstance().analyze(methodKey);
  }

  public static void runPathAnalyzer() {
    ImpurityAnalyzer.getInstance().runPathAnalyzer();
  }

  public static float getPathWeight(String methodKey) {
    return PathAnalyzer.getInstance().getScore(methodKey);
    
  }

  public static int getWeight(String methodKey) {
    runAnalyzers(methodKey);
    int weight = 0;
    weight += NullLiteralAnalyzer.getInstance().getScore(methodKey);
    weight += NullableFieldAccessAnalyzer.getInstance().getScore(methodKey);
    weight += ExternalInvocationAnalyzer.getInstance().getScore(methodKey);
    weight += NullableGetterInvocationAnalyzer.getInstance().getScore(methodKey);
    weight += NullableParameterAccessAnalyzer.getInstance().getScore(methodKey);
    return weight;
    
  }
}
