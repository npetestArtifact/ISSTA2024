package npetest.synthesizer.search.mutation;

import npetest.commons.spoon.TypeUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.mutation.MutationCore;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class TestCaseMutator {
  public abstract CtLocalVariable<?> selectTargetObject(TestCase testCase);

  public abstract TestCase mutateObjects(TestCase testCase);

  public abstract TestCase changeValuesInArguments(TestCase testCase);

  /**
   * Get local variables that are created before the bound position. (inclusive)
   */
  protected List<CtLocalVariable<?>> collectCandidateMutationTargets(TestCase testCase, int boundPosition) {
    List<CtLocalVariable<?>> localVariables = new ArrayList<>();
    for (int i = 0; i <= boundPosition; i++) {
      CtLocalVariable<?> targetObject = getTargetObjectIfMutable(testCase, i);
      if (targetObject != null) {
        localVariables.add(targetObject);
      }
    }

    List<CtLocalVariable<?>> filteredLocalVariables = new ArrayList<>();
    List<CtLocalVariableReference<?>> lvRefs = testCase.getCtStatements()
            .filterChildren(new TypeFilter<>(CtLocalVariableReference.class)).list();
    for (CtLocalVariable<?> localVariable : localVariables) {
      int refCount = (int) lvRefs.stream()
              .filter(lvRef -> lvRef.getSimpleName().equals(localVariable.getSimpleName())).count();
      for (int i = 0; i < refCount; i++) {
        filteredLocalVariables.add(localVariable);
      }
    }
    return filteredLocalVariables;
  }

  protected Map<Integer, CtLocalVariable<?>> getCtLocalVariablesWithPositions(TestCase testCase, int boundPosition) {
    Map<Integer, CtLocalVariable<?>> targetObjects = new HashMap<>();
    for (int i = 0; i <= boundPosition; i++) {
      CtLocalVariable<?> targetObject = getTargetObjectIfMutable(testCase, i);
      if (targetObject != null) {
        targetObjects.put(i, targetObject);
      }
    }
    return targetObjects;
  }

  protected CtLocalVariable<?> getTargetObjectIfMutable(TestCase testCase, int index) {
    CtStatement statement = testCase.getCtStatements().getStatement(index);
    if (statement instanceof CtLocalVariable<?> &&
            testCase.getCreatedTypes().containsKey(((CtLocalVariable<?>) statement).getSimpleName()) &&
            GenerationHistory.isMutable((((CtLocalVariable<?>) statement).getType()).getQualifiedName()) &&
            !TypeUtils.isJavaLangObject(((CtLocalVariable<?>) statement).getType()) &&
            !TypeUtils.isJavaLangClass(((CtLocalVariable<?>) statement).getType()) &&
            !TypeUtils.isNull(((CtLocalVariable<?>) statement).getDefaultExpression().getType()) &&
            !TypeUtils.isPrimitive(((CtLocalVariable<?>) statement).getDefaultExpression().getType()) &&
            !TypeUtils.isString(((CtLocalVariable<?>) statement).getDefaultExpression().getType()) &&
            !TypeUtils.isBoxingType(((CtLocalVariable<?>) statement).getDefaultExpression().getType()) &&
            !TypeUtils.isPrimitiveArray(((CtLocalVariable<?>) statement).getDefaultExpression().getType())) {
      return (CtLocalVariable<?>) statement;
    } else {
      return null;
    }
  }

  protected TestCase mutateObject(TestCase testCase, CtLocalVariable<?> target) {
    CtStatementList ctStatements = MutationCore.mutateDeclaredObject(testCase.getCtStatements(), target);
    if (ctStatements == null) {
      return null;
    }
    return new TestCase(testCase, ctStatements, TestGenContext.getCtPackage(), testCase.getMainType(),
            TestGenContext.getStartTime());
  }

  protected TestCase changeArgument(TestCase testCase, CtExpression<?> target) {
    CtStatementList ctStatements = MutationCore.mutateExpression(testCase.getCtStatements(), target);
    return new TestCase(testCase, ctStatements, TestGenContext.getCtPackage(), testCase.getMainType(),
            TestGenContext.getStartTime());
  }
}
