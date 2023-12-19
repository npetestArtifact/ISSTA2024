package npetest.synthesizer.search.mut;

import npetest.commons.Configs;

public class MUTSelectorFactory {
  public static MUTSelector create(Configs.MUTSelectionStrategy mutSelectionStrategy) {
    switch (mutSelectionStrategy) {
      case DEFAULT:
        return new DefaultMUTSelector();
      case FEEDBACK:
        return new GuidedMUTSelector();
      default:
        throw new UnsupportedOperationException();
    }
  }
}
