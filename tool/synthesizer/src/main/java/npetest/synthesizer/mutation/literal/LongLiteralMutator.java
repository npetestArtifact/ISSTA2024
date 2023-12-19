package npetest.synthesizer.mutation.literal;

import npetest.commons.misc.RandomUtils;

import java.util.Arrays;
import java.util.Objects;

class LongLiteralMutator extends LiteralMutator<Long> {
  enum Operator {
    NEGATE, PLUS_SMALL, PLUS_LARGE, RANDOM
  }

  @Override
  protected Long mutateT(Long originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case NEGATE:
        return -originalValue;
      case PLUS_SMALL:
        return originalValue + (long) Math.floor(RandomUtils.random.nextGaussian() * 100);
      case PLUS_LARGE:
        return originalValue + (long) Math.floor(RandomUtils.random.nextGaussian() * 10000L);
      case RANDOM:
        return RandomUtils.random.nextLong();
    }
    // unreachable
    return originalValue;
  }
}
