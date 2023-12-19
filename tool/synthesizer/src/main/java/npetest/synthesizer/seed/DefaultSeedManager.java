package npetest.synthesizer.seed;

import npetest.commons.misc.RandomUtils;
import npetest.language.sequence.TestCase;

import java.util.Collection;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSeedManager extends SeedManager {
  private static final Logger logger = LoggerFactory.getLogger(DefaultSeedManager.class);

  public void setup(Collection<TestCase> seedPool) {
    this.seedPool = new HashSet<>();
    this.seedPool.addAll(seedPool);
  }

  @Override
  public TestCase choose() {
    TestCase select = RandomUtils.select(seedPool);
    lastlySelectedSeed = select;
    return select;
  }

  @Override
  public void update(TestCase seedTestCase, TestCase mutant) {
    // if (mutant.getScore() > seedTestCase.getScore()) {
       seedPool.add(mutant);
    // } 
    mutants.add(mutant);
  }
}
