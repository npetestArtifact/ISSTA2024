package npetest.synthesizer.search.mutation;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.misc.WeightedCollection;
import npetest.commons.spoon.TypeUtils;
import npetest.language.sequence.SequenceUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.MutantGenerator;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.mutation.MutationUtil;
import spoon.reflect.code.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuidedTestCaseMutator extends TestCaseMutator {
  private static final Logger logger = LoggerFactory.getLogger(GuidedTestCaseMutator.class);

  // WE randomly select targets with 20% probaiblity
  // We follow the guidance of weighted sampling with 80% probability
  @Override
  public CtLocalVariable<?> selectTargetObject(TestCase testCase) {
    CtLocalVariable<?> result = null;

    if (RandomUtils.p() < 0.2) {
      
      int faultPosition = SequenceUtils.getFaultTriggeringStatementIndex(testCase);
      int boundPosition = faultPosition != -1 ? faultPosition : testCase.length() - 1;
      List<CtLocalVariable<?>> candidates = collectCandidateMutationTargets(testCase, boundPosition);

      result = RandomUtils.select(candidates);

      testCase.setMutatedVar(result.getSimpleName());
      testCase.updateMutationTrial(result.getSimpleName());

      return result;
    } else {

//    int faultPosition = SequenceUtils.getFaultTriggeringStatementIndex(testCase);
//    int boundPosition = faultPosition == -1 ? testCase.length() - 1 : faultPosition - 1;
//    Map<Integer, CtLocalVariable<?>> localVariables = getCtLocalVariablesWithPositions(testCase, boundPosition);
//    if (localVariables.isEmpty()) {
//      return null;
//    }
//    Map<Integer, Integer> accessCountMap = analyzeObjectAccessCount(testCase, localVariables);

      // for (String tt : testCase.getMutationScoreMap().keySet()) {
      //   logger.info("TARGET SCORE: " + tt.toString() + " - " + Float.toString(testCase.getMutationScore(tt)));
      //   logger.info("# SELECTED: " + Float.toString(testCase.getMutationTrial(tt)));
      // }

      WeightedCollection<String> wc = new WeightedCollection<>(testCase.getMutationScoreMap());
      String targetVariableName = wc.next();
      if (targetVariableName == null) {
        int faultPosition = SequenceUtils.getFaultTriggeringStatementIndex(testCase);
        int boundPosition = faultPosition == -1 ? testCase.length() - 1 : faultPosition - 1;
        Map<Integer, CtLocalVariable<?>> localVariables = getCtLocalVariablesWithPositions(testCase, boundPosition);
        result = RandomUtils.select(localVariables.values());
      } else {
        result = testCase.findObject(targetVariableName);
      }

      testCase.setMutatedVar(result.getSimpleName());
      testCase.updateMutationTrial(result.getSimpleName());
      
      return result;
    }
  }

  private Map<Integer, Integer> analyzeObjectAccessCount(TestCase testCase,
                                                         Map<Integer, CtLocalVariable<?>> localVariables) {
    Map<Integer, Integer> accessCountMap = new HashMap<>();

    for (Entry<Integer, CtLocalVariable<?>> entry : localVariables.entrySet()) {
      int index = entry.getKey();
      CtLocalVariable<?> lv = entry.getValue();
      Set<ExecutableKey> relevantCalledMethods = new HashSet<>();
      for (int i = index + 1; i < testCase.length(); i++) {
        CtStatement statement = testCase.getCtStatements().getStatement(i);
        List<CtVariableAccess<?>> accesses = statement.filterChildren(new TypeFilter<>(CtVariableAccess.class))
                .select((CtVariableAccess<?> va) -> va.getVariable().getSimpleName().equals(lv.getSimpleName()))
                .list();
        if (!accesses.isEmpty() && testCase.getMethodTrace().containsKey(i)) {
          relevantCalledMethods.addAll(testCase.getMethodTrace().get(i));
        }
      }

      int accessCount = 0;
      for (ExecutableKey relevantCalledMethod : relevantCalledMethods) {
        accessCount += relevantCalledMethod.getCtElement()
                .filterChildren(new TypeFilter<>(CtVariableAccess.class))
                .select((CtVariableAccess<?> va) -> !TypeUtils.isPrimitive(va.getType()))
                .select((CtVariableAccess<?> va) -> va.getRoleInParent().equals(CtRole.TARGET)
                        || va.getRoleInParent().equals(CtRole.ARGUMENT))
                .select((CtVariableAccess<?> va) -> !TypeUtils.isJavaLangObject(va.getType())
                        && !TypeUtils.isJavaLangClass(va.getType()))
                .select((CtVariableAccess<?> va) -> lv.getType().getQualifiedName().equals(va.getType().getQualifiedName())
                        || lv.getDefaultExpression().getType().getQualifiedName().equals(va.getType().getQualifiedName()))
                .list().size();
      }
      accessCountMap.put(index, accessCount);
    }
    return accessCountMap;
  }

  private Map<Integer, Integer> analyzeMutationCount(TestCase testCase, Map<Integer, CtLocalVariable<?>> localVariables) {
    Map<Integer, Integer> mutationCountMap = new HashMap<>();

    CtStatementList ctStatements = testCase.getCtStatements();
    for (Entry<Integer, CtLocalVariable<?>> entry : localVariables.entrySet()) {
      int index = entry.getKey();
      CtLocalVariable<?> lv = entry.getValue();
      int mutationCount = 0;
      for (int i = index + 1; i < ctStatements.getStatements().size(); i++) {
        CtStatement statement = ctStatements.getStatement(i);
        List<CtVariableAccess<?>> targetExpressions = statement.filterChildren(new TypeFilter<>(CtVariableAccess.class))
                .select((CtVariableAccess<?> va) -> va.getVariable().getSimpleName().equals(lv.getSimpleName())
                        && va.getRoleInParent().equals(CtRole.TARGET))
                .list();
        if (!targetExpressions.isEmpty()) {
          mutationCount++;
        }
      }
      mutationCountMap.put(index, mutationCount);
    }
    return mutationCountMap;
  }

  @Override
  public TestCase mutateObjects(TestCase testCase) {
    TestGenContext.startMutation(testCase);
    CtLocalVariable<?> localVariable = selectTargetObject(testCase);
    if (localVariable == null) {
      return null;
    }
    return mutateObject(testCase, localVariable);
  }

  @Override
  public TestCase changeValuesInArguments(TestCase testCase) {
    List<CtExpression<?>> argumentTargets = MutationUtil.getValuesUsedForArguments(testCase.getCtStatements());
    CtExpression<?> target = RandomUtils.select(argumentTargets);
    return changeArgument(testCase, target);
  }
}
