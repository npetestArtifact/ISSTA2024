package npetest.synthesizer.generators.stoppers;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.ParameterKey;
import npetest.commons.misc.Timer;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.search.mut.MUTSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtExecutable;

import java.util.HashSet;
import java.util.Set;

public class FixedParameterSpaceStoppingCondition implements StoppingCondition {
  private static final Logger logger = LoggerFactory.getLogger(FixedParameterSpaceStoppingCondition.class);

  private final MUTSelector mutSelector;

  private final Timer timer;

  public FixedParameterSpaceStoppingCondition(MUTSelector mutSelector) {
    this.mutSelector = mutSelector;
    this.timer = new Timer();
    this.timer.setup(Timer.GLOBAL_TIMER.calculateRemainingTime() / 2);
  }

  @Override
  public boolean hasReached() {
    if (this.timer.isTimeout()) {
      return true;
    }

    Set<ExecutableKey> muts = mutSelector.getMUTs();

    Set<ExecutableKey> unsaturatedMUTs = new HashSet<>();
    for (ExecutableKey mutKey : muts) {
      CtExecutable<?> mut = mutKey.getCtElement();
      int size = mut.getParameters().size();
      int wholeSpace = 2;
      for (int i = 0; i < size; i++) {
        ParameterKey paramKey = ParameterKey.of(mut, i);
        Integer paramSpace = InvocationGenerationContext.parameterTypeSpaces.getOrDefault(paramKey, 0);
        logger.debug("Space: {} - {}", paramKey, paramSpace);
        wholeSpace *= paramSpace;
      }

      Integer generatedCount = TestGenContext.generationCount.getOrDefault(mutKey, 0);
      if (generatedCount <= wholeSpace) {
        unsaturatedMUTs.add(mutKey);
      }
    }

    return unsaturatedMUTs.isEmpty();
  }

  @Override
  public void reset() {

  }
}
