package npetest.synthesizer.search.mutation;

import npetest.commons.misc.RandomUtils;
import npetest.language.sequence.SequenceUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.mutation.MutationUtil;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;

import java.util.List;

public class DefaultTestCaseMutator extends TestCaseMutator {

  @Override
  public CtLocalVariable<?> selectTargetObject(TestCase testCase) {
    int faultPosition = SequenceUtils.getFaultTriggeringStatementIndex(testCase);
    int boundPosition = faultPosition != -1 ? faultPosition : testCase.length() - 1;
    List<CtLocalVariable<?>> candidates = collectCandidateMutationTargets(testCase, boundPosition);
    return RandomUtils.select(candidates);
  }

  @Override
  public TestCase mutateObjects(TestCase testCase) {
    TestGenContext.startMutation(testCase);
    CtLocalVariable<?> localVariable = selectTargetObject(testCase);
    if (localVariable == null) {
      return null;
    }
    return mutateObject(testCase, localVariable);
  }

  @Override
  public TestCase changeValuesInArguments(TestCase testCase) {
    List<CtExpression<?>> argumentTargets = MutationUtil.getValuesUsedForArguments(testCase.getCtStatements());
    CtExpression<?> target = RandomUtils.select(argumentTargets);
    return changeArgument(testCase, target);
  }
}
