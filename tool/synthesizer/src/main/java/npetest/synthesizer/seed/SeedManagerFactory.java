package npetest.synthesizer.seed;

import npetest.commons.Configs;

public class SeedManagerFactory {
  public static SeedManager create(Configs.SeedSelectionStrategy type) {
    switch (type) {
      case DEFAULT:
        return new DefaultSeedManager();
      case FEEDBACK:{
        return new SmartSeedManager();
      }
      // case TEST:
      //   return new 
      default:
        return null;
    }
  }
}
