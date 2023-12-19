package npetest.synthesizer;

import npetest.analysis.dynamicanalysis.DynamicInformation;
import npetest.commons.exceptions.SynthesisFailure;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.SequenceUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.ObjectGenContext;
import npetest.synthesizer.generators.stoppers.StoppingCondition;
import npetest.synthesizer.result.SeedGenerationResult;
import npetest.synthesizer.result.TestEvaluator;
import npetest.synthesizer.search.mutation.TestCaseMutator;
import npetest.synthesizer.seed.SeedManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class MutantGenerator extends Generator {
  private static final Logger logger = LoggerFactory.getLogger(MutantGenerator.class);

  private TestCaseMutator testCaseMutator;

  private SeedManager seedManager;

  private MutantGenerator() {
  }

  public void setup(SeedGenerationResult seedGenerationResult) {
    this.stoppingCondition.reset();

    this.seedManager.setup(seedGenerationResult.getUniqueResults());
  }

  @Override
  protected boolean doGeneration() {
    TestCase seedTestCase = seedManager.choose();
    if (seedTestCase == null) {
      logger.info("Seed pool becomes empty!");
      return false;
    }

    try {
      logger.debug("* Reusing existing test case...");
      TestCase mutant = mutateTestCase(seedTestCase);
      if (mutant == null) {
        logger.debug("* Failed to generate mutant");
      } else {
        seedManager.update(seedTestCase, mutant); 
      }
    } catch (SynthesisFailure e) {
      logger.debug("**** Synthesis failed during mutant generation");
    } catch (Exception e) {
      logger.debug("**** Error occurred during mutant generation");
    } finally {
      ObjectGenContext.clear();
      DynamicInformation.reset();
    }
    return true;
  }

  private TestCase mutateTestCase(TestCase testCase) {
    TestCase mutant = testCase;
    if (RandomUtils.random.nextDouble() < 0.5) {
      mutant = testCaseMutator.mutateObjects(mutant);
      TestEvaluator.evaluate(mutant);
//      if (failedByNPEDuringMutation(mutant)) {
//        mutant = mutateTypeParameterAndSaveSeed(mutant);
//        TestEvaluator.evaluate(mutant);
//      }
    } else {
      mutant = testCaseMutator.changeValuesInArguments(mutant);
      TestEvaluator.evaluate(mutant);
    }
    return mutant;
  }

  private TestCase mutateTypeParameterAndSaveSeed(TestCase failedMutant) {
    int faultTriggeringStatementIndex = SequenceUtils.getFaultTriggeringStatementIndex(failedMutant);
    CtStatement statement = failedMutant.getCtStatements().getStatement(faultTriggeringStatementIndex);
    List<CtLiteral<?>> nullArguments = statement.filterChildren(new TypeFilter<>(CtLiteral.class))
            .select((CtLiteral<?> literal) -> TypeUtils.isNull(literal.getType()))
            .select((CtLiteral<?> literal) -> literal.getRoleInParent().equals(CtRole.ARGUMENT))
            .list();

    List<CtLocalVariable<?>> mutationTargets = new ArrayList<>();
    for (CtLiteral<?> nullArgument : nullArguments) {
      List<CtTypeReference<?>> typeCasts = nullArgument.getTypeCasts();
      if (typeCasts.isEmpty()) {
        continue;
      }

      CtTypeReference<?> ctTypeReference = typeCasts.get(0);

      if (ObjectGenContext.hasFailed(ctTypeReference)) {
        CtInvocation<?> invocation = nullArgument.getParent(CtInvocation.class);
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtVariableAccess<?>
                && ((CtVariableAccess<?>) target).getVariable() instanceof CtLocalVariableReference<?>) {
          CtLocalVariable<?> localVariable = ((CtLocalVariableReference<?>)
                  ((CtVariableAccess<?>) target).getVariable()).getDeclaration();
          mutationTargets.add(localVariable);
        }
      }
    }

    return failedMutant;
  }

  private boolean failedByNPEDuringMutation(TestCase mutatedTestCase) {
    ExecutionResult result = mutatedTestCase.getResult();
    boolean npe = result.isNPE();
    if (!npe) {
      return false;
    }

    int faultTriggeringStatementIndex = SequenceUtils.getFaultTriggeringStatementIndex(mutatedTestCase);
    return faultTriggeringStatementIndex < mutatedTestCase.length() - 1;
  }

  public List<TestCase> getResults() {
    List<TestCase> results = new ArrayList<>(seedManager.getSeedPool());

    results.addAll(seedManager.getMutants());    

    return results;
  }

  public static class Builder {
    private StoppingCondition stoppingCondition;

    private TestCaseMutator testCaseMutator;

    private SeedManager seedManager;

    public MutantGenerator.Builder stoppingCondition(StoppingCondition stoppingCondition) {
      this.stoppingCondition = stoppingCondition;
      return this;
    }

    public MutantGenerator.Builder mutationTargetSelector(TestCaseMutator testCaseMutator) {
      this.testCaseMutator = testCaseMutator;
      return this;
    }

    public MutantGenerator.Builder seedManager(SeedManager seedManager) {
      this.seedManager = seedManager;
      return this;
    }

    public MutantGenerator build() {
      MutantGenerator mutantGenerator = new MutantGenerator();
      mutantGenerator.stoppingCondition = stoppingCondition;
      mutantGenerator.seedManager = seedManager;
      mutantGenerator.testCaseMutator = testCaseMutator;
      return mutantGenerator;
    }
  }
}
