package npetest.synthesizer.mutation;

import npetest.commons.Configs;
import npetest.commons.filters.ConstantAccessFilter;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.models.ModeledType;
import npetest.commons.models.ModeledTypeFactory;
import npetest.commons.models.ModeledTypes;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.generators.MethodInvocationGenerator;
import npetest.synthesizer.generators.ObjectInstantiator;
import npetest.synthesizer.generators.PrimitiveExpressionGenerator;
import npetest.synthesizer.mutation.literal.LiteralMutationHelper;
import npetest.synthesizer.search.method.DefaultModifyingMethodSearcher;
import npetest.synthesizer.search.method.GuidedModifyingMethodSearcher;
import npetest.synthesizer.search.method.ModifyingMethodSearcher;
import npetest.synthesizer.search.value.ConstantFieldSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MutationCore {
  
  private static final Logger logger = LoggerFactory.getLogger(MutationCore.class);

  private MutationCore() {
  }

  private static ModifyingMethodSearcher modifyingMethodSearcher;

  private static ExecutableKey selectedMutationMethod;

  static LiteralMutationHelper literalMutationHelper = new LiteralMutationHelper();

  public static void setup(Configs.TestCaseMutationStrategy testCaseMutationStrategy) {
    switch (testCaseMutationStrategy) {
      case DEFAULT:
        modifyingMethodSearcher = new DefaultModifyingMethodSearcher();
      case FEEDBACK:
        modifyingMethodSearcher = new GuidedModifyingMethodSearcher();
      default:
        modifyingMethodSearcher = new DefaultModifyingMethodSearcher();
    }
  }

  public static void updateModifyingMethodScore(TestCase testCase, TestCase result) {
    modifyingMethodSearcher.updateScore(testCase, result, selectedMutationMethod);
  }

  private enum ArrayMutationOperator {
    ADD, REMOVE, CLEAR
  }

  public static CtStatementList mutateFaultyObject(CtStatementList statements,
                                                   CtLocalVariable<?> localVariable,
                                                   List<CtTypeReference<?>> relatedTypes) {
    CtTypeReference<?> declType = localVariable.getType();
    CtTypeReference<?> actualType = localVariable.getDefaultExpression().getType();
    boolean methodFromActualType = false;
    CtMethod<?> modifyingMethod;
    if (declType.isArray()) {
      return mutateArrayObject(statements, localVariable);
    } else if (ModeledTypes.contains(declType)) {
      return mutateDeclaredObject(statements, localVariable);
    } else {
      modifyingMethod = modifyingMethodSearcher.getTypeRelatedMethod(declType, relatedTypes);
      if (modifyingMethod == null) {
        methodFromActualType = true;
        modifyingMethod = modifyingMethodSearcher.getTypeRelatedMethod(actualType, relatedTypes);
      }
    }
    if (modifyingMethod == null) {
      GenerationHistory.addImmutableType(declType.getQualifiedName());
      GenerationHistory.addImmutableType(actualType.getQualifiedName());
      return null;
    }

    CtStatementList clone = statements.clone();
    int index = clone.getStatements().indexOf(localVariable);
    CtVariableAccess<?> variableAccess = CodeFactory.createVariableAccess(localVariable);
    CtTypeReference<?> instanceType = methodFromActualType ? actualType : declType;
    Sequence absSequence = MethodInvocationGenerator.generateMethodInvocation(modifyingMethod, variableAccess, instanceType, null);
    CtStatementList mutationStatements = absSequence.getCtStatementList();
    return appendMutationStatementsAfter(clone, mutationStatements, index);
  }

  public static CtStatementList mutateDeclaredObject(CtStatementList statements,
                                                     CtLocalVariable<?> localVariable) {
    CtTypeReference<?> declType = localVariable.getType();
    CtTypeReference<?> actualType = localVariable.getDefaultExpression().getType();
    boolean methodFromActualType = false;
    CtMethod<?> modifyingMethod;

    if (ModeledTypes.contains(declType)) {
      ModeledType<?> modeledType = ModeledTypeFactory.create(declType);
      List<CtMethod<?>> modifyingMethods = modeledType.getModifyingMethods();
      modifyingMethod = RandomUtils.select(modifyingMethods);
    } else {
      modifyingMethod = modifyingMethodSearcher.select(declType);

      
      if (modifyingMethod == null) {
        methodFromActualType = true;
        modifyingMethod = modifyingMethodSearcher.select(actualType);

      }
    }

    if (modifyingMethod == null) {
      GenerationHistory.addImmutableType(declType.getQualifiedName());
      GenerationHistory.addImmutableType(actualType.getQualifiedName());
      return null;
    }

    selectedMutationMethod = ExecutableKey.of(modifyingMethod);

    CtStatementList clone = statements.clone();
    List<Integer> targetLocations = new ArrayList<>();
    int lvLocation = clone.getStatements().indexOf(localVariable);

    for (int i = lvLocation + 1; i < clone.getStatements().size(); i++) {
      CtStatement statement = clone.getStatement(i);
      List<CtLocalVariableReference<?>> lvRefs = statement.filterChildren(new TypeFilter<>(CtLocalVariableReference.class))
              .select((CtLocalVariableReference<?> lvRef) -> lvRef.getSimpleName().equals(localVariable.getSimpleName()))
              .list();
      if (!lvRefs.isEmpty()) {
        targetLocations.add(i);
      }
    }

    Integer select = RandomUtils.select(targetLocations);
    if (select == null) {
      return null;
    }
    int index = select;
    CtVariableAccess<?> variableAccess = CodeFactory.createVariableAccess(localVariable);
    CtTypeReference<?> instanceType = methodFromActualType ? actualType : declType;
    Sequence absSequence = MethodInvocationGenerator.generateMethodInvocation(modifyingMethod, variableAccess, instanceType, null);
    CtStatementList mutationStatements = absSequence.getCtStatementList();
    
    return insertMutationStatementsBefore(clone, mutationStatements, index);
  }

  private static CtStatementList mutateArrayObject(CtStatementList statements, CtLocalVariable<?> localVariable) {
    CtStatementList clone = statements.clone();
    int index = clone.getStatements().indexOf(localVariable);
    CtLocalVariable<?> lv = clone.getStatement(index);
    CtNewArray<?> newArray = (CtNewArray<?>) lv.getDefaultExpression();
    CtTypeReference<?> componentType = ((CtArrayTypeReference<?>) newArray.getType()).getComponentType();
    List<CtExpression<?>> elements = newArray.getElements();
    int size = elements.size();
    ArrayMutationOperator op = size == 0 ? ArrayMutationOperator.ADD
            : ArrayMutationOperator.values()[RandomUtils.random.nextInt(ArrayMutationOperator.values().length)];
    switch (op) {
      case ADD:
        CtExpression<?> randomPrimitiveExpression = PrimitiveExpressionGenerator.createRandomPrimitiveExpression(componentType);
        if (size == 0) {
          List<CtExpression<?>> newElements = new ArrayList<>();
          newElements.add(randomPrimitiveExpression);
          newArray.setElements(newElements);
        } else {
          elements.add(randomPrimitiveExpression);
        }
        break;
      case REMOVE:
        List<CtExpression<?>> sublist = RandomUtils.sublist(elements, size - 1);
        newArray.setElements(sublist);
        break;
      case CLEAR:
        List<CtExpression<?>> emptyList = new ArrayList<>();
        newArray.setElements(emptyList);
        break;
    }
    return clone;
  }

  public static CtStatementList insertMutationStatementsBefore(CtStatementList targetStatements,
                                                              CtStatementList mutationStatements,
                                                              int index) {
    int position = index;
    for (CtStatement mutationStatement : mutationStatements) {
      targetStatements.addStatement(position++, mutationStatement);
    }

    CtStatement mutationMethodInvocation = targetStatements.getStatement(position - 1);
    if (mutationMethodInvocation instanceof CtLocalVariable<?>
            && ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression() instanceof CtInvocation<?>
            && ((CtLocalVariable<?>) mutationMethodInvocation).getType().getQualifiedName().equals(
            ((CtInvocation<?>) ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression()).getTarget()
                    .getType().getQualifiedName())) {
      /* Replace variable reference to mutated object, if mutation method creates a new object */
      CtVariableAccess<?> oldVariableAccess =
              ((CtVariableAccess<?>) ((CtInvocation<?>) ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression()).getTarget());
      String oldVariableName = oldVariableAccess.getVariable().getSimpleName();
      for (int i = position; i < targetStatements.getStatements().size(); i++) {
        CtStatement statement = targetStatements.getStatement(i);
        List<CtVariableAccess<?>> replacementTargets = statement.filterChildren(new TypeFilter<>(CtVariableAccess.class))
                .select((CtVariableAccess<?> va) -> va.getVariable().getSimpleName().equals(oldVariableName))
                .list();
        replacementTargets.forEach(va -> va.replace(
                CodeFactory.createVariableAccess((CtLocalVariable<?>) mutationMethodInvocation)));
      }
    }

    ASTUtils.removeDuplicatedVariable(targetStatements);
    return targetStatements;
  }

  public static CtStatementList appendMutationStatementsAfter(CtStatementList targetStatements,
                                                              CtStatementList mutationStatements,
                                                              int index) {
    int position = index + 1;
    for (CtStatement mutationStatement : mutationStatements) {
      targetStatements.addStatement(position++, mutationStatement);
    }

    CtStatement mutationMethodInvocation = targetStatements.getStatement(position - 1);
    if (mutationMethodInvocation instanceof CtLocalVariable<?>
            && ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression() instanceof CtInvocation<?>
            && ((CtLocalVariable<?>) mutationMethodInvocation).getType().getQualifiedName().equals(
            ((CtInvocation<?>) ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression()).getTarget()
                    .getType().getQualifiedName())) {
      /* Replace variable reference to mutated object, if mutation method creates a new object */
      CtVariableAccess<?> oldVariableAccess =
              ((CtVariableAccess<?>) ((CtInvocation<?>) ((CtLocalVariable<?>) mutationMethodInvocation).getDefaultExpression()).getTarget());
      String oldVariableName = oldVariableAccess.getVariable().getSimpleName();
      for (int i = position; i < targetStatements.getStatements().size(); i++) {
        CtStatement statement = targetStatements.getStatement(i);
        List<CtVariableAccess<?>> replacementTargets = statement.filterChildren(new TypeFilter<>(CtVariableAccess.class))
                .select((CtVariableAccess<?> va) -> va.getVariable().getSimpleName().equals(oldVariableName))
                .list();
        replacementTargets.forEach(va -> va.replace(
                CodeFactory.createVariableAccess((CtLocalVariable<?>) mutationMethodInvocation)));
      }
    }

    ASTUtils.removeDuplicatedVariable(targetStatements);
    return targetStatements;
  }

  public static CtStatementList mutateExpression(CtStatementList statements, CtExpression<?> targetElement) {
    if (TypeUtils.isNull(targetElement.getType())) {
      return mutateNullObject(statements, targetElement);
    } else if (targetElement instanceof CtLiteral<?>) {
      return mutateLiteral(statements, (CtLiteral<?>) targetElement);
    } else if (targetElement instanceof CtFieldRead<?> && targetElement.getType().isEnum()) {
      return mutateEnum(statements, (CtFieldRead<?>) targetElement);
    } else if (targetElement instanceof CtFieldRead<?>) {
      return mutateConstantReference(statements, (CtFieldRead<?>) targetElement);
    }
    return statements.clone();
  }

  private static CtStatementList mutateNullObject(CtStatementList statements, CtExpression<?> targetElement) {
    CtStatementList clone =  statements.clone();
    CtTypeReference<?> type = targetElement.getType();
    CtStatement enclosingStatement = targetElement.getParent(CtStatement.class);
    enclosingStatement = enclosingStatement.getRoleInParent().equals(CtRole.DEFAULT_EXPRESSION) ?
            enclosingStatement.getParent(CtLocalVariable.class) : enclosingStatement;
    int index = statements.getStatements().indexOf(enclosingStatement);
    Sequence sequence = ObjectInstantiator.instantiate(VariableType.fromInstanceType(type), false);
    if (!sequence.isInlineValue()) {
      int i = 0;
      for (CtStatement ctStatement : sequence.getCtStatementList()) {
        clone.addStatement(index + i++, ctStatement);
      }
    }
    return clone;
  }

  private static CtStatementList mutateLiteral(CtStatementList statements, CtLiteral<?> targetElement) {
    CtStatementList clone = statements.clone();
    CtLiteral<?> targetLiteral = (CtLiteral<?>) clone.filterChildren(new TypeFilter<>(CtLiteral.class))
            .select((CtLiteral<?> l) -> l.equals(targetElement))
            .list().get(0);
    CtTypeReference<?> primitiveType = TypeUtils.isNull(targetLiteral.getType())
            ? targetLiteral.getTypeCasts().get(0) : targetLiteral.getType();
    CtLiteral<?> substitutionLiteral = literalMutationHelper.mutateLiteral(targetLiteral, primitiveType);
    targetLiteral.replace(substitutionLiteral);
    return clone;
  }

  private static CtStatementList mutateConstantReference(CtStatementList statements,
                                                         CtFieldRead<?> targetElement) {
    CtStatementList clone = statements.clone();
    CtFieldRead<?> targetFieldRead = (CtFieldRead<?>) clone.filterChildren(new TypeFilter<>(CtFieldRead.class))
            .select((CtFieldRead<?> f) -> f.equals(targetElement))
            .list().get(0);
    TestCase seedTestCase = TestGenContext.getSeedTestCase();
    List<CtFieldRead<?>> candidateConstantReferences = new ArrayList<>();
    List<ExecutableKey> executableKeys = seedTestCase.getMethodTrace().get(seedTestCase.length() - 1);
    List<CtExecutable<?>> queryRoots = executableKeys.stream().map(ExecutableKey::getCtElement).collect(Collectors.toList());
    for (CtExecutable<?> queryRoot : queryRoots) {
      candidateConstantReferences.addAll(queryRoot.filterChildren(ConstantAccessFilter.INSTANCE.setType(
                      targetFieldRead.getType()))
              .map(CtElement::clone).list());
    }

    if (candidateConstantReferences.isEmpty()) {
      Collection<CtField<?>> ctFields = ConstantFieldSearcher.getInstance().searchFields(
              TestGenContext.getCtPackage(), targetElement.getType());
      List<CtField<?>> filteredFields = ctFields.stream()
              .filter((CtField<?> f) -> !(f.getSimpleName().equals(targetElement.getVariable().getSimpleName())
                      && targetElement.getVariable().getDeclaringType().getQualifiedName().equals(
                      f.getDeclaringType().getQualifiedName())))
              .collect(Collectors.toList());

      candidateConstantReferences.addAll((filteredFields.isEmpty() ? ctFields : filteredFields)
              .stream()
              .map(CodeFactory::createConstantFieldRead)
              .collect(Collectors.toList()));
    }
    targetFieldRead.replace(RandomUtils.select(candidateConstantReferences));
    return clone;
  }

  private static CtStatementList mutateEnum(CtStatementList statements, CtFieldRead<?> targetElement) {
    CtStatementList clone = statements.clone();
    CtFieldRead<?> targetEnumRead = (CtFieldRead<?>) clone.filterChildren(new TypeFilter<>(CtFieldRead.class))
            .select((CtFieldRead<?> l) -> l.equals(targetElement))
            .list().get(0);
    CtEnum<?> enumDecl = (CtEnum<?>) targetEnumRead.getTarget().getType().getTypeDeclaration();
    List<CtEnumValue<?>> candidates = new ArrayList<>(enumDecl.getEnumValues());
    CtEnumValue<?> currentEnum = (CtEnumValue<?>) targetElement.getVariable().getDeclaration();
    candidates.remove(currentEnum);
    CtEnumValue<?> substitutionEnumDecl = RandomUtils.select(candidates);
    CtFieldRead<?> substitutionEnumValue = CodeFactory.createEnumRead(enumDecl, substitutionEnumDecl);
    targetEnumRead.replace(substitutionEnumValue);
    return clone;
  }
}