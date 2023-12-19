package npetest.synthesizer.seed;

import npetest.language.sequence.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class SeedManager {
  protected Collection<TestCase> seedPool;

  protected final List<TestCase> mutants = new ArrayList<>();

  protected TestCase lastlySelectedSeed;

  public abstract void setup(Collection<TestCase> seedPool);

  public abstract TestCase choose();

  public abstract void update(TestCase seedTestCase, TestCase mutant);

  public Collection<TestCase> getSeedPool() {
    return seedPool;
  }

  public List<TestCase> getMutants() {
    return mutants;
  }
}
