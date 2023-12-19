package npetest.synthesizer.mutation.literal;

import npetest.commons.misc.RandomUtils;

import java.util.Arrays;
import java.util.Objects;

class ShortLiteralMutator extends LiteralMutator<Short> {

  enum Operator {
    NEGATE, PLUS_SMALL, PLUS_LARGE, RANDOM
  }

  @Override
  protected Short mutateT(Short originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case NEGATE:
        return (short) -originalValue;
      case PLUS_SMALL:
        return (short) (originalValue + Math.floor(RandomUtils.random.nextGaussian() * 5));
      case PLUS_LARGE:
        return (short) (originalValue + Math.floor(RandomUtils.random.nextGaussian() * 1000));
      case RANDOM:
        return (short) RandomUtils.random.nextInt(Short.MAX_VALUE);
    }
    // unreachable
    return originalValue;
  }
}
