package npetest.synthesizer.seed;

import npetest.analysis.dynamicanalysis.BasicBlockCoverage;
import npetest.analysis.npeanalysis.NullableFieldAccessAnalyzer;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.misc.WeightedCollection;
import npetest.language.sequence.TestCase;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartSeedManager extends SeedManager {
  private static final Logger logger = LoggerFactory.getLogger(SmartSeedManager.class);
  Map<TestCase, Float> normalizedScoreMap = new HashMap<>();

  Map<TestCase, Float> coverageScoreMap = new HashMap<>();

  Map<TestCase, Float> overallScoreMap = new HashMap<>();

  WeightedCollection<TestCase> wc;

  boolean poolChanged;

  public void setup(Collection<TestCase> seedPool) {
    this.seedPool = new HashSet<>();
    this.seedPool.addAll(seedPool);

    updateWeightedCollection();
  }

  private void updateWeightedCollection() {

    Map<TestCase, Float> scoreMap = this.seedPool.stream()
            .collect(Collectors.toMap(t -> t, TestCase::getScore, (v1, v2) -> v1));
    float max = scoreMap.values().stream().reduce(Float::max).orElse(-1f);
    float min = scoreMap.values().stream().reduce(Float::min).orElse(-1f);

    for (Map.Entry<TestCase, Float> entry : scoreMap.entrySet()) {
      TestCase tc = entry.getKey();
      float newScore = max == min ? 0f : (entry.getValue() - min) / (max - min);
      normalizedScoreMap.put(entry.getKey(), newScore);
      overallScoreMap.put(tc, newScore);
    }

    wc = new WeightedCollection<>(overallScoreMap);

    // for (TestCase testCase : seedPool) {
    //   List<ExecutableKey> executableKeys = testCase.getMethodTrace().get(testCase.length() - 1);
    //   if (executableKeys == null) {
    //     coverageScoreMap.put(testCase, 0f);
    //   } else {
    //     float coverage = 0f;
    //     for (ExecutableKey executableKey : executableKeys) {
    //       coverage += BasicBlockCoverage.getInstance().getCoverage(executableKey.toString());
    //     }
    //     float score = 1f - coverage / executableKeys.size();
    //     coverageScoreMap.put(testCase, score);
    //   }
    // }

    // for (Map.Entry<TestCase, Float> entry : coverageScoreMap.entrySet()) {
    //   TestCase tc = entry.getKey();
    //   float npeScore = normalizedScoreMap.get(tc);
    //   float coverageScore = entry.getValue();
    //   overallScoreMap.put(tc, 0.5f * (npeScore + coverageScore));
    // }
    // wc = new WeightedCollection<>(overallScoreMap);


  }

  @Override
  public TestCase choose() {

    if (poolChanged) {
      updateWeightedCollection();
    }

    if (RandomUtils.p() < 0.2) {
      TestCase select = RandomUtils.select(seedPool);
      return select;
    } else {
      return wc.next();
    }
  }

  @Override
  public void update(TestCase seedTestCase, TestCase mutant) {
    if (mutant.getScore() > seedTestCase.getScore()) {
      poolChanged = true;
      seedPool.add(mutant);
    } else {
      poolChanged = false;
    }

    seedTestCase.checkUpdate(poolChanged, seedTestCase, mutant);
    
    mutants.add(mutant);
  }
}
