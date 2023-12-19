package npetest.synthesizer.generators.stoppers;

import npetest.analysis.dynamicanalysis.MethodTrace;
import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.misc.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPEMethodCoverageStoppingCondition implements StoppingCondition {
  private static final Logger logger = LoggerFactory.getLogger(NPEMethodCoverageStoppingCondition.class);

  private final int monitoringInterval;

  private final Timer timer;

  private final MonitoringQueue<Integer> coverageQueue = new MonitoringQueue<>(5);

  private int counter = 0;

  public NPEMethodCoverageStoppingCondition() {
    this.monitoringInterval = CtModelExt.INSTANCE.getMUTs().size() * 3;
    this.timer = new Timer();
    this.timer.setup(Timer.GLOBAL_TIMER.calculateRemainingTime() / 2);
  }

  @Override
  public boolean hasReached() {
    if (this.timer.isTimeout()) {
      return true;
    }

    if (counter == 0) {
      counter = (counter + 1) % monitoringInterval;
      coverageQueue.add(MethodTrace.getInstance().getGlobalNPEMethodCoverage().size());
      if (coverageQueue.size() < coverageQueue.getCapacity()) {
        return false;
      }
      if (Configs.VERBOSE) {
        logger.info("Elapsed time: {}, NPE method coverage: {}", Timer.GLOBAL_TIMER.getElapsedTime(), coverageQueue);
      }
      return coverageQueue.get(0) != 0 &&
              (float) coverageQueue.get(4) / coverageQueue.get(0) < 1.1f;
    } else {
      counter = (counter + 1) % monitoringInterval;
      return false;
    }
  }

  @Override
  public void reset() {
    coverageQueue.clear();
  }
}
