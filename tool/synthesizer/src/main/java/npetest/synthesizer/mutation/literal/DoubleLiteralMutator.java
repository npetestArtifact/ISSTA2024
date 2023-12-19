package npetest.synthesizer.mutation.literal;

import npetest.commons.misc.RandomUtils;

import java.util.Arrays;
import java.util.Objects;

class DoubleLiteralMutator extends LiteralMutator<Double> {
  enum Operator {
    NEGATE, PLUS_SMALL, MINUS_SMALL, PLUS_LARGE, MINUS_LARGE, RANDOM
  }

  @Override
  protected Double mutateT(Double originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case NEGATE:
        return  -originalValue;
      case PLUS_SMALL:
        return originalValue + Math.floor(RandomUtils.random.nextGaussian() * 100);
      case MINUS_SMALL:
        return originalValue - Math.floor(RandomUtils.random.nextGaussian() * 100);
      case PLUS_LARGE:
        return originalValue + Math.floor(RandomUtils.random.nextGaussian() * 10000.0);
      case MINUS_LARGE:
        return originalValue - Math.floor(RandomUtils.random.nextGaussian() * 10000.0);
      case RANDOM:
        double value = RandomUtils.random.nextGaussian();
        return RandomUtils.random.nextBoolean() ? value : -value;
    }
    // unreachable
    return originalValue;
  }
}
