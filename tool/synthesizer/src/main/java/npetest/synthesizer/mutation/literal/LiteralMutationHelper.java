package npetest.synthesizer.mutation.literal;

import npetest.commons.exceptions.SynthesisFailure;
import npetest.commons.filters.LiteralFilter;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.synthesizer.context.TestGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.stream.Collectors;

public class LiteralMutationHelper {
  private static final Logger logger = LoggerFactory.getLogger(LiteralMutationHelper.class);

  private final BooleanLiteralMutator booleanLiteralMutator = new BooleanLiteralMutator();
  private final ByteLiteralMutator byteLiteralMutator = new ByteLiteralMutator();
  private final ShortLiteralMutator shortLiteralMutator = new ShortLiteralMutator();
  private final IntegerLiteralMutator integerLiteralMutator = new IntegerLiteralMutator();
  private final LongLiteralMutator longLiteralMutator = new LongLiteralMutator();
  private final FloatLiteralMutator floatLiteralMutator = new FloatLiteralMutator();
  private final DoubleLiteralMutator doubleLiteralMutator = new DoubleLiteralMutator();
  private final CharacterLiteralMutator characterLiteralMutator = new CharacterLiteralMutator();
  private final StringLiteralMutator stringLiteralMutator = new StringLiteralMutator();

  public CtLiteral<?> mutateLiteral(CtLiteral<?> originalLiteral, CtTypeReference<?> instanceType) {
    Object valueToMutate = null;
    if (TestGenContext.getSeedTestCase() != null && RandomUtils.random.nextBoolean()) {
      valueToMutate = mutateWithLocalTestContext(originalLiteral.getValue(), instanceType);
    }

    Object mutantValue = valueToMutate != null
            ? mutateValue(valueToMutate, instanceType)
            : mutateValue(originalLiteral.getValue(), instanceType);

    return CodeFactory.createLiteral(mutantValue);
  }

  private Object mutateWithLocalTestContext(Object value, CtTypeReference<?> primitiveType) {
    List<CtLiteral<?>> otherLiterals = TestGenContext.getSeedTestCase().getCtStatements()
            .filterChildren(LiteralFilter.INSTANCE.setType(primitiveType))
            .select((CtLiteral<?> l) -> !l.getValue().equals(value))
            .list();
    List<Object> otherValues = otherLiterals.stream().map(CtLiteral::getValue).collect(Collectors.toList());
    return RandomUtils.select(otherValues);
  }

  private Object mutateValue(Object value, CtTypeReference<?> instanceType) {
    Object mutantValue;
    if (TypeUtils.isBoolean(instanceType)) {
      mutantValue = booleanLiteralMutator.mutate(value);
    } else if (TypeUtils.isByte(instanceType)) {
      mutantValue = byteLiteralMutator.mutate(value);
    } else if (TypeUtils.isShort(instanceType)) {
      mutantValue = shortLiteralMutator.mutate(value);
    } else if (TypeUtils.isInteger(instanceType)) {
      mutantValue = integerLiteralMutator.mutate(value);
    } else if (TypeUtils.isLong(instanceType)) {
      mutantValue = longLiteralMutator.mutate(value);
    } else if (TypeUtils.isFloat(instanceType)) {
      mutantValue = floatLiteralMutator.mutate(value);
    } else if (TypeUtils.isDouble(instanceType)) {
      mutantValue = doubleLiteralMutator.mutate(value);
    } else if (TypeUtils.isChar(instanceType)) {
      mutantValue = characterLiteralMutator.mutate(value);
    } else if (TypeUtils.isString(instanceType)) {
      mutantValue = stringLiteralMutator.mutate(value);
    } else {
      // unreachable
      logger.error("Reach unreachable state in LiteralMutationHelper!");
      throw new SynthesisFailure("Reach unreachable state in LiteralMutationHelper");
    }
    return mutantValue;
  }
}
