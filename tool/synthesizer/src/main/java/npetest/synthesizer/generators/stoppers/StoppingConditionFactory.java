package npetest.synthesizer.generators.stoppers;

import npetest.commons.Configs;
import npetest.synthesizer.search.mut.MUTSelector;

public class StoppingConditionFactory {
  public static StoppingCondition create(Configs.StoppingConditionType type, MUTSelector mutSelector) {
    switch (type) {
      case TIME:
        return new TimeBudgetStoppingCondition();
      case NPE_METHOD_COVERAGE:
        return new NPEMethodCoverageStoppingCondition();
      case NPE_METHOD_BASIC_BLOCK_COVERAGE:
        return new NPEMethodBasicBlockCoverageStoppingCondition();
      case FIXED_PARAMETER_SPACE:
        return new FixedParameterSpaceStoppingCondition(mutSelector);
      default:
        throw new UnsupportedOperationException();
    }
  }
}
