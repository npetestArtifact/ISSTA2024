package npetest.synthesizer.generators;

import npetest.commons.Configs;
import npetest.commons.misc.RandomUtils;
import npetest.commons.models.ModeledTypes;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.TestGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.reference.CtTypeReference;

public abstract class ObjectInstantiator {
  public static final Logger logger = LoggerFactory.getLogger(ObjectInstantiator.class);

  private ObjectInstantiator() {
  }

  public static Sequence instantiate(VariableType inputType, boolean isReceiverObject) {
    logger.debug("* Instantiating {}...", inputType);

    Sequence sequence;
    CtTypeReference<?> instanceType = inputType.getInstanceType();

    if (!isReceiverObject && !TypeUtils.isPrimitive(inputType.getDeclType()) && RandomUtils.p() < Configs.NULL_PROBABILITY) {
      return new Sequence(CodeFactory.createNullExpression(inputType.getDeclType()));
    }

    if (TypeUtils.isPrimitive(inputType.getDeclType())) {
      sequence = new Sequence(PrimitiveExpressionGenerator.createRandomPrimitiveExpression(instanceType));
      logger.debug("  Primitive type instantiation result: {}", sequence.getId());
      return sequence;
    }

    if (!GenerationHistory.isInstantiable(instanceType.getQualifiedName())) {
      logger.debug("  {} is in black list -> instantiation failed", instanceType);
      return new Sequence(CodeFactory.createNullExpression(inputType.getDeclType()));
    }

    if (ObjectPool.exploit(inputType)) {
      logger.debug("-> Exploiting object pool...");
      sequence = ObjectPool.selectExistingObject(inputType);
      logger.debug("  Selected existing object: {}", sequence.getId());
      return sequence;
    }

    if (TestGenContext.testGenerationTimeOut() && !ModeledTypes.contains(inputType.getInstanceType())) {
      logger.debug("  Object generation timeout -> use null value");
      return new Sequence(CodeFactory.createNullExpression(inputType.getDeclType()));
    }

    sequence = ObjectGenerator.createNewObject(inputType);
    if (sequence != null && !sequence.isNull()) {
      ObjectPool.put(inputType, sequence);
    }

    if (sequence == null) {
      sequence = ObjectGenerator.generateWithNonTypicalGenerator(inputType);
      if (sequence == null) {
        logger.debug("  Object creation failed -> use null value");
        return new Sequence(CodeFactory.createNullExpression(inputType.getDeclType()));
      }
    }

    return sequence;
  }

}
