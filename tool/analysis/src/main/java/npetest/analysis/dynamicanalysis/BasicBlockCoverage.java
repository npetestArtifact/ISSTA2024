package npetest.analysis.dynamicanalysis;

import javassist.bytecode.analysis.ControlFlow.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BasicBlockCoverage {
  private static BasicBlockCoverage instance = new BasicBlockCoverage();

  public static BasicBlockCoverage getInstance() {
    return instance;
  }

  private final Map<String, Map<Integer, Boolean>> basicBlockHits = new HashMap<>();

  // recorded method in this run
  private final Set<String> methodHits = new HashSet<>();

  private final Map<String, Integer> methodBasicBlockCoveredCount = new HashMap<>();

  private boolean enabled;

  public void enable() {
    enabled = true;
  }

  public void disable() {
    enabled = false;
  }

  public void reset() {
    enabled = false;
    methodHits.clear();
  }


  public void setupBasicBlockInfo(String methodKey, Block[] blocks) {
    String methodKeyEscaped = methodKey.replace("\\#", "#");
    Map<Integer, Boolean> hits = basicBlockHits.computeIfAbsent(methodKeyEscaped, m -> new HashMap<>());
    if (!hits.isEmpty()) {
      return;
    }
    for (Block block : blocks) {
      int index = block.index();
      hits.put(index, false);
    }
  }

  @SuppressWarnings("unused")
  public void recordBasicBlockHit(String methodKey, String index) {
    if (enabled) {
      int id = Integer.parseInt(index);
      basicBlockHits.computeIfAbsent(methodKey, m -> new HashMap<>()).put(id, true);
      methodHits.add(methodKey);
    }
  }

  public void updateBasicBlockCoverage() {
    for (String methodKey : methodHits) {
      updateBasicBlockCoverage(methodKey);
    }
  }

  public void updateBasicBlockCoverage(String methodKey) {
    Map<Integer, Boolean> hits = basicBlockHits.computeIfAbsent(methodKey, m -> new HashMap<>());
    int coveredBlockCount = (int) hits.values().stream().filter(b -> b).count();
    methodBasicBlockCoveredCount.put(methodKey, coveredBlockCount);
  }

  public int getCoveredBasicBlockCount(String methodKey) {
    return methodBasicBlockCoveredCount.getOrDefault(methodKey, 0);
  }

  public float getCoverage(String methodKey) {
    Map<Integer, Boolean> hits = basicBlockHits.computeIfAbsent(methodKey, m -> new HashMap<>());
    return hits.isEmpty() ? 0.0f : (float) getCoveredBasicBlockCount(methodKey) / hits.size();
  }
}
