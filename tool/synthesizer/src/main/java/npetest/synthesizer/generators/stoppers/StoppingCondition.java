package npetest.synthesizer.generators.stoppers;

public interface StoppingCondition {
  boolean hasReached();

  void reset();
}
