package npetest.synthesizer;

import npetest.synthesizer.generators.stoppers.StoppingCondition;

public abstract class Generator {
  protected StoppingCondition stoppingCondition;

  public void run() {
    while (!stoppingCondition.hasReached()) {
      if (!doGeneration()) {
        break;
      }
    }
  }

  protected abstract boolean doGeneration();
}
