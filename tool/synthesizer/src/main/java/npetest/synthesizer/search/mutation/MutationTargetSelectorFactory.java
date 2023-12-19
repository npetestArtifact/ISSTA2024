package npetest.synthesizer.search.mutation;

import npetest.commons.Configs;

public class MutationTargetSelectorFactory {

  public static TestCaseMutator create(Configs.TestCaseMutationStrategy testCaseMutationStrategy) {
    switch (testCaseMutationStrategy) {
      case DEFAULT:
        return new DefaultTestCaseMutator();
      case FEEDBACK:
        return new GuidedTestCaseMutator();
      default:
        throw new UnsupportedOperationException();
    }
  }
}
