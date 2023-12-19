package npetest.commons.misc;

public class Timer {
  public static final Timer GLOBAL_TIMER = new Timer();

  /* start time in milliseconds */
  private long start;

  /* time budget in milliseconds */
  private long timeBudget;

  /**
   * Set up the timer with a time budget.
   *
   * @param timeBudget time budget in seconds passed from command line
   */
  public void setup(float timeBudget) {
    this.start = System.currentTimeMillis();
    this.timeBudget = (long) (timeBudget * 1000f);
  }

  /**
   * @return remaining time in seconds
   */
  public float calculateRemainingTime() {
    long current = System.currentTimeMillis();
    long elapsed = current - this.start;
    return (float) (timeBudget - elapsed) / 1000;
  }

  /**
   * @return elapsed time in seconds
   */
  public float getElapsedTime() {
    long current = System.currentTimeMillis();
    return ((float) (current - this.start)) / 1000;
  }

  /**
   * @return true if time is up
   */
  public boolean isTimeout() {
    return calculateRemainingTime() < 0;
  }

  public String toLogMessageFormat() {
    return "TIME: " + getElapsedTime() + "s";
  }
}
