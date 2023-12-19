package npetest.synthesizer.generators.stoppers;

import npetest.commons.misc.Timer;

public class TimeBudgetStoppingCondition implements StoppingCondition {
  private final Timer timer;

  public TimeBudgetStoppingCondition() {
    this.timer = new Timer();
  }

  @Override
  public boolean hasReached() {
    return timer.isTimeout();
  }

  @Override
  public void reset() {
    this.timer.setup(Timer.GLOBAL_TIMER.calculateRemainingTime());
  }

  public void reset(float timeBudget) {
    this.timer.setup(timeBudget);
  }
}
