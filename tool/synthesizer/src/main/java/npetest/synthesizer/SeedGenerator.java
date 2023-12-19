package npetest.synthesizer;

import npetest.analysis.dynamicanalysis.DynamicInformation;
import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.exceptions.SynthesisFailure;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.Debugger;
import npetest.commons.misc.Timer;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.ObjectGenContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.generators.MethodInvocationGenerator;
import npetest.synthesizer.generators.ObjectInstantiator;
import npetest.synthesizer.generators.stoppers.StoppingCondition;
import npetest.synthesizer.generators.stoppers.TimeBudgetStoppingCondition;
import npetest.synthesizer.result.SeedGenerationResult;
import npetest.synthesizer.result.TestEvaluator;
import npetest.synthesizer.search.mut.MUTSelector;
import npetest.synthesizer.typeadaption.TypeConcretizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.Set;

public class SeedGenerator extends Generator {
  private static final Logger logger = LoggerFactory.getLogger(SeedGenerator.class);

  private MUTSelector mutSelector;

  private final SeedGenerationResult result = new SeedGenerationResult();

  private SeedGenerator() {}

  public void setup() {
    if (this.stoppingCondition instanceof TimeBudgetStoppingCondition) {
      ((TimeBudgetStoppingCondition) this.stoppingCondition).reset(
              Timer.GLOBAL_TIMER.calculateRemainingTime() / Configs.PROPORTION_SEED_GENERATION_TIME);
    } else {
      this.stoppingCondition.reset();
    }
    this.mutSelector.setup(CtModelExt.INSTANCE.getMUTs());
  }

  public Set<ExecutableKey> getMUTs() {
    return mutSelector.getMUT();
  }

  @Override
  protected boolean doGeneration() {
    ExecutableKey mutKey = mutSelector.choose();
    if (mutKey == null) {
      logger.info("* MUTs become empty!");
      return false;
    }
    logger.debug("* Selected MUT: {}", mutKey);

    try {
      CtType<?> mainType = setupContextAndGetMainType(mutKey);
      TestCase testCase = generateTestCase(mainType, mutKey);
      if (testCase == null) {
        logger.debug("* Failed to generate new test case for `{}`", mutKey);
      } else {
        logger.debug("SEED GENERATION STARTS");
        TestEvaluator.evaluate(testCase);
        result.recordTestCase(testCase);
        Integer count = TestGenContext.generationCount.getOrDefault(mutKey, 0);
        TestGenContext.generationCount.put(mutKey, count + 1);
        for (TestCase interimNPETriggeringObjectSequence : TestGenContext.getInterimNPETriggeringObjectSequences()) {
          TestEvaluator.evaluate(interimNPETriggeringObjectSequence);
          result.recordNPETriggeringObject(interimNPETriggeringObjectSequence);
        }
      }
    } catch (SynthesisFailure e) {
      logger.debug("**** Synthesis failed during test generation for {}", mutKey);
      Debugger.logSynthesisFailure(mutKey, e);
    } catch (Exception e) {
      logger.debug("**** Error occurred during test generation for {}", mutKey);
      Debugger.logToolError(mutKey, e);
    } finally {
      ObjectGenContext.clear();
      DynamicInformation.reset();
    }
    return true;
  }

  public SeedGenerationResult getResult() {
    return result;
  }

  public TestCase generateTestCase(CtType<?> mainType, ExecutableKey mutKey) {
    TestGenContext.startGeneration(mainType);
    CtMethod<?> mut = (CtMethod<?>) mutKey.getCtElement();
    CtTypeReference<?> receiverObjectType;
    CtStatementList resultStatements = CodeFactory.createStatementList();
    if (!mut.isStatic()) {
      receiverObjectType = TypeConcretizer.concretizeReceiverObjectType(mainType);
      VariableType receiverObjectVariableType =
              receiverObjectType.getTypeDeclaration().isAbstract() || receiverObjectType.isInterface()
                      ? VariableType.fromSuccessfulAdaption(receiverObjectType, TypeUtils.nullType())
                      : VariableType.fromInstanceType(receiverObjectType);
      Sequence receiverObject = ObjectInstantiator.instantiate(receiverObjectVariableType, true);
      if (receiverObject.isNull()) {
        return null;
      }

      /* receiverObject cannot be inlineValue since it is neither null nor a value of primitive type */
      if (!receiverObject.isInlineValue()) {
        for (CtStatement ctStatement : receiverObject.getCtStatementList()) {
          resultStatements.addStatement(ctStatement);
        }
      }
      CtExpression<?> receiverObjectAccess = receiverObject.getAccessExpression();
      Sequence sequence = MethodInvocationGenerator.generateMethodInvocation(mut, receiverObjectAccess, receiverObjectType,
              null);
      for (CtStatement ctStatement : sequence.getCtStatementList()) {
        resultStatements.addStatement(ctStatement);
      }
    } else {
      receiverObjectType = mut.getDeclaringType().getReference();
      CtTypeAccess<?> typeAccess = CodeFactory.createTypeAccess(mut.getDeclaringType());
      Sequence sequence = MethodInvocationGenerator.generateMethodInvocation(mut, typeAccess, receiverObjectType, null);
      for (CtStatement ctStatement : sequence.getCtStatementList()) {
        resultStatements.addStatement(ctStatement);
      }

    }
    return new TestCase(resultStatements, TestGenContext.getCtPackage(), mainType, TestGenContext.getStartTime());
  }

  private CtType<?> setupContextAndGetMainType(ExecutableKey mutKey) {
    CtType<?> mainType;
    CtMethod<?> mut = (CtMethod<?>) mutKey.getCtElement();
    if (!mut.isStatic()) {
      mainType = TypeConcretizer.selectConcreteMainClass(mut);
      if (mainType == null) {
        return null;
      }
    } else {
      mainType = mut.getDeclaringType();
    }
    return mainType;
  }

  public static class Builder {
    private StoppingCondition stoppingCondition;

    private MUTSelector mutSelector;

    public Builder stoppingCondition(StoppingCondition stoppingCondition) {
      this.stoppingCondition = stoppingCondition;
      return this;
    }

    public Builder mutSelector(MUTSelector mutSelector) {
      this.mutSelector = mutSelector;
      return this;
    }

    public SeedGenerator build() {
      SeedGenerator seedGenerator = new SeedGenerator();
      seedGenerator.stoppingCondition = stoppingCondition;
      seedGenerator.mutSelector = mutSelector;
      return seedGenerator;
    }
  }
}
