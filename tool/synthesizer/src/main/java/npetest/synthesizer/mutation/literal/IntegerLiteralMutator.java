package npetest.synthesizer.mutation.literal;

import npetest.commons.misc.RandomUtils;

import java.util.Arrays;
import java.util.Objects;

class IntegerLiteralMutator extends LiteralMutator<Integer> {

  enum Operator {
    NEGATE, PLUS_SMALL, PLUS_LARGE, RANDOM
  }

  @Override
  protected Integer mutateT(Integer originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case NEGATE:
        return -originalValue;
      case PLUS_SMALL:
        return originalValue + (int) Math.floor(RandomUtils.random.nextGaussian() * 10);
      case PLUS_LARGE:
        return originalValue + (int) Math.floor(RandomUtils.random.nextGaussian() * 10000);
      case RANDOM:
        return RandomUtils.random.nextInt();
    }
    // unreachable
    return originalValue;
  }
}
