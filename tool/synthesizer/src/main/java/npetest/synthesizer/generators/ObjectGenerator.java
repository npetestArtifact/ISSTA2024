package npetest.synthesizer.generators;

import npetest.analysis.executor.ExecutionHelper;
import npetest.analysis.fault.FaultAnalysis;
import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.cluster.ConstructorCluster;
import npetest.commons.exceptions.SynthesisFailure;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.models.ModeledType;
import npetest.commons.models.ModeledTypeFactory;
import npetest.commons.models.ModeledTypeUtils;
import npetest.commons.models.ModeledTypes;
import npetest.commons.spoon.TypeAccessibilityChecker;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.Sequence;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.ObjectGenContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.mutation.MutationCore;
import npetest.synthesizer.mutation.MutationUtil;
import npetest.synthesizer.search.constructor.ConstructorSearcher;
import npetest.synthesizer.typeadaption.TypeArgumentSubstitution;
import npetest.synthesizer.typeadaption.TypeConcretizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

import java.util.*;
import java.util.stream.Collectors;

public class ObjectGenerator {
  private ObjectGenerator() {
  }

  private static final Logger logger = LoggerFactory.getLogger(ObjectGenerator.class);

  private static final ConstructorSearcher constructorSearcher = new ConstructorSearcher();

  public static Sequence createNewObject(VariableType inputType) {
    logger.debug("* Creating new object...");
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    ObjectGenContext.startGeneration(instanceType);
    if (ObjectGenContext.checkWorkingObjectTypesLimit() || ObjectGenContext.checkInstanceRecursionLimit(instanceType)) {
      Set<CtField<?>> constantObjects = constructorSearcher.getConstantObject(instanceType);
      if (constantObjects.isEmpty()) {
        logger.debug("The synthesizer has reached the max recursion depth for {}", instanceType);
        ObjectGenContext.failure(instanceType);
        return new Sequence(CodeFactory.createNullExpression(inputType.getDeclType()));
      }
      CtField<?> constantObject = RandomUtils.select(constantObjects);
      ObjectGenContext.done(instanceType);
      return generateObjectWithConstantObject(instanceType, constantObject);
    }

    int i = 0;
    Sequence sequence = null;
    while (sequence == null && i < Configs.OBJECT_GENERATION_TRIAL) {
      logger.debug(" - Object creation trial {} for {} ...", i, inputType);
      sequence = generateAndValidateObject(inputType);
      i++;
    }
    logger.debug("* Object creation result for {}: {}", inputType, sequence == null ? "Failed!" : sequence.getId());
    ObjectGenContext.done(instanceType);
    return sequence;
  }

  private static Sequence generateAndValidateObject(VariableType inputType) {
    Sequence sequence = generateObjectSequence(inputType);
    if (sequence == null) {
      return null;
    }

    ExecutionResult result = ExecutionHelper.INSTANCE.runObjectSequence(
            TestGenContext.getCtPackage(), sequence.getCtStatementList(), inputType.getInstanceType());
    if (!result.isCompiled()) {
      return null;
    }

    if (result.isNormalExecution()) {
      return sequence;
    }

    if (result.isNPE()) {
      TestGenContext.addNPETriggeringObjectSequence(sequence.getCtStatementList(), result);
    }

    int i = 0;
    Sequence mutatedSequence = null;
    while (mutatedSequence == null && i <= Configs.OBJECT_GENERATION_MUTANT_TRIAL) {
      logger.debug(" -- Object mutation trial {} for {} ...", i, inputType);
      if (RandomUtils.p() < 0.5) {
        mutatedSequence = mutateAndValidateObject(inputType, sequence, result.getFault());
      }
      i++;
    }

    return mutatedSequence;
  }

  private static Sequence mutateAndValidateObject(VariableType inputType, Sequence objectSequence, Throwable fault) {
    CtStatementList originalStatements = objectSequence.getCtStatementList();
    CtStatementList mutatedStatements = mutateFailedSequenceRandomly(originalStatements, fault);

    if (mutatedStatements == null) {
      return null;
    }

    ExecutionResult mutantExecutionResult = ExecutionHelper.INSTANCE.runObjectSequence(TestGenContext.getCtPackage(),
            mutatedStatements, inputType.getInstanceType());
    if (!mutantExecutionResult.isCompiled()) {
      return null;
    }

    if (mutantExecutionResult.isNormalExecution()) {
      return new Sequence(mutatedStatements);
    }

    if (mutantExecutionResult.isNPE()) {
      TestGenContext.addNPETriggeringObjectSequence(mutatedStatements, mutantExecutionResult);
    }

    return null;
  }

  /**
   * @param inputType concrete type of the object
   * @return Fresh object sequence without any extra method calls
   */
  public static Sequence generateObjectSequence(VariableType inputType) {
    /* instanceType should be complete concrete type */
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    CtTypeReference<?> declType = inputType.getDeclType();
    Sequence objectSequence;
    if (inputType.inputNull()) {
      if (declType.isArray()) {
        objectSequence = new Sequence(CodeFactory.createNullExpression(declType));
      } else if (inputType.successAdaption() || CtModelExt.INSTANCE.searchGenerators(declType).isEmpty()) {
        objectSequence = new Sequence(CodeFactory.createNullExpression(declType));
      } else {
        objectSequence = generateWithNonTypicalGenerator(inputType);
      }
    } else if (TypeUtils.isString(instanceType)) {
      return new Sequence(PrimitiveExpressionGenerator.createRandomPrimitiveExpression(instanceType));
    } else if (TypeUtils.isBoxingType(instanceType)) {
      return new Sequence(PrimitiveExpressionGenerator.createRandomPrimitiveExpression(instanceType));
    } else if (instanceType.isEnum()) {
      objectSequence = new Sequence(ObjectGenerator.generateEnumValueAccess(instanceType));
    } else if (TypeUtils.isPrimitiveArray(instanceType)) {
      objectSequence = ArrayGenerator.generatePrimitiveArray((CtArrayTypeReference<?>) instanceType);
    } else if (instanceType.isArray()) {
      // non-primitive array
      objectSequence = ArrayGenerator.generateNonPrimitiveArray((CtArrayTypeReference<?>) instanceType);
    } else if (ModeledTypes.contains(instanceType)) {
      objectSequence = generateModeledObject(declType);
    } else if (instanceType.isAnonymous()) {
      objectSequence = generateAnonymousObject(inputType);
    } else if (TypeUtils.isJavaLangClass(instanceType)) {
      objectSequence = generateJavaLangClass(inputType);
    } else {
      objectSequence = generateObject(inputType);
    }
    return objectSequence;
  }

  private static Sequence generateModeledObject(CtTypeReference<?> declType) {
    try {
      ModeledType<?> modeledType = ModeledTypeFactory.create(declType);
      CtTypeReference<?> instanceType = modeledType.getAdaptedType();
      CtConstructor<?> constructor = modeledType.getConstructor();
      if (constructor != null) {
        Sequence sequence = ConstructorCallGenerator.generateObjectWithConstructor(declType, instanceType, constructor);
        ModeledTypeUtils.postProcess(modeledType, constructor, sequence.getCtStatementList());
        return sequence;
      }

      CtMethod<?> factoryMethod = modeledType.getFactoryMethod();
      if (factoryMethod != null) {
        Sequence sequence = generateObjectWithFactoryMethod(declType, instanceType, factoryMethod);
        if (sequence != null) {
          ModeledTypeUtils.postProcess(modeledType, factoryMethod, sequence.getCtStatementList());
          return sequence;
        }
      }

    } catch (Exception e) {
      logger.debug("  -> Failed to create modeled object for {}", declType.getQualifiedName());
    }
    return createNullObject(declType);
  }

  public static Sequence generateAnonymousObject(VariableType inputType) {
    CtTypeReference<?> anonymousTypeReference = inputType.getInstanceType();
    CtType<?> anonymousType = anonymousTypeReference.getTypeDeclaration();
    CtTypeReference<?> declType = anonymousType.getParent(CtMethod.class).getType();

    ConstructorCluster cluster = ConstructorCluster.getInstance();
    CtMethod<?> factoryMethod = cluster.getAnonymousClassFactoryMethod(anonymousTypeReference);
    if (factoryMethod == null) {
      logger.debug("* Anonymous type {} is not returned object", anonymousType.getQualifiedName());
      return createNullObject(declType);
    }

    if (!TypeAccessibilityChecker.isGloballyAssignableAnonymousClass(anonymousType)) {
      logger.debug("* Failed to create an instance of anonymous class {}", anonymousType.getQualifiedName());
      return createNullObject(declType);
    }

    if (!factoryMethod.isStatic()) {
      logger.debug("* {} is not created by static method, which is not supported yet",
              anonymousType.getQualifiedName());
      return createNullObject(declType);
    }

    return generateObjectWithFactoryMethod(declType, anonymousTypeReference, factoryMethod);
  }

  public static CtExpression<?> generateEnumValueAccess(CtTypeReference<?> enumType) {
    CtEnum<?> ctEnum = (CtEnum<?>) enumType.getTypeDeclaration();
    CtEnumValue<?> enumValue = RandomUtils.select(ctEnum.getEnumValues());
    if (enumValue == null) {
      // Possible?
      throw new SynthesisFailure("Empty enum values for " + enumType + '\n');
    }
    return CodeFactory.createEnumRead(ctEnum, enumValue);
  }

  public static Sequence generateJavaLangClass(VariableType inputType) {
    CtTypeReference<?> declType = inputType.getDeclType();
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    List<CtTypeReference<?>> actualTypeArguments = instanceType.getActualTypeArguments();
    CtTypeReference<?> actualReferredType;
    CtTypeReference<?> referredType = actualTypeArguments.get(0);
    if (referredType instanceof CtWildcardReference) {
      actualReferredType = ((CtWildcardReference) referredType).getBoundingType();
    } else {
      actualReferredType = referredType;
    }
    CtTypeAccess<?> typeAccess = CodeFactory.createTypeAccess(actualReferredType);
    CtFieldReference<?> fieldReference = CodeFactory.createFieldReference();
    fieldReference.setSimpleName("class");
    CtFieldRead<?> fieldRead = CodeFactory.createFieldRead();
    fieldRead.setValueByRole(CtRole.VARIABLE, fieldReference);
    fieldRead.setValueByRole(CtRole.TARGET, typeAccess);
    fieldRead.setValueByRole(CtRole.TYPE, instanceType);
    CtLocalVariable<?> newLocalVariable = CodeFactory.createNewLocalVariable(declType, fieldRead);
    return new Sequence(newLocalVariable);
  }

  public static Sequence generateObject(VariableType inputType) {
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    CtTypeReference<?> declType = inputType.getDeclType();
    CtElement api = constructorSearcher.select(instanceType);
    if (api instanceof CtConstructor<?>) {
      logger.debug("  -> Selected constructor: {}", ExecutableKey.of((CtConstructor<?>) api));
      return ConstructorCallGenerator.generateObjectWithConstructor(declType, instanceType, (CtConstructor<?>) api);
    } else if (api instanceof CtMethod<?>) {
      logger.debug("  -> Selected factory method: {}", ExecutableKey.of((CtMethod<?>) api));
      return generateObjectWithFactoryMethod(declType, instanceType, (CtMethod<?>) api);
    } else if (api instanceof CtField<?>) {
      logger.debug("  -> Selected constant field: {}#{}",
              ((CtField<?>) api).getDeclaringType().getQualifiedName(), ((CtField<?>) api).getSimpleName());
      return generateObjectWithConstantObject(instanceType, (CtField<?>) api);
    } else {
      logger.debug("  -> Failed to find any possible api for constructing {}", instanceType);
      return createNullObject(declType);
    }
  }

  private static Sequence generateObjectWithConstantObject(CtTypeReference<?> instanceType,
                                                           CtField<?> constantObject) {
    CtFieldRead<?> fieldRead = CodeFactory.createConstantFieldRead(constantObject);
    CtLocalVariable<?> objectDeclaration = CodeFactory.createNewLocalVariable(instanceType, fieldRead);
    return new Sequence(objectDeclaration);
  }

  public static Sequence generateObjectWithFactoryMethod(CtTypeReference<?> declType, CtTypeReference<?> instanceType,
                                                         CtMethod<?> factoryMethod) {
    GenerationHistory.updateUsedGenerators(factoryMethod);
    CtStatementList result = CodeFactory.createStatementList();
    if (!factoryMethod.isStatic()) {
      CtType<?> factoryType = factoryMethod.getDeclaringType();
      CtTypeReference<?> concreteFactoryType = factoryType.isGenerics()
              ? TypeConcretizer.adaptTypeParameterOfFactoryMethod(factoryType, instanceType)
              : factoryType.getReference();


      Sequence factoryObjectCreationSequence =
              ObjectInstantiator.instantiate(VariableType.fromInstanceType(concreteFactoryType), true);
      if (factoryObjectCreationSequence.isNull()) {
        return null;
      }

      for (CtStatement ctStatement : factoryObjectCreationSequence.getCtStatementList()) {
        result.addStatement(ctStatement);
      }

      CtExpression<?> factoryObjectAccess = factoryObjectCreationSequence.getAccessExpression();
      Sequence factoryMethodCallSequence =
              MethodInvocationGenerator.generateMethodInvocation(factoryMethod, factoryObjectAccess, concreteFactoryType,
                      declType);
      for (CtStatement ctStatement : factoryMethodCallSequence.getCtStatementList()) {
        result.addStatement(ctStatement);
      }
    } else {
      CtTypeReference<?> factoryMethodAccessorType = factoryMethod.getDeclaringType().getReference();
      CtTypeAccess<?> typeAccess = CodeFactory.createTypeAccess(factoryMethodAccessorType);

      Sequence sequence = MethodInvocationGenerator.generateMethodInvocation(factoryMethod, typeAccess, instanceType,
              declType);
      for (CtStatement ctStatement : sequence.getCtStatementList()) {
        result.addStatement(ctStatement);
      }
    }
    return new Sequence(result);
  }

  public static Sequence createObjectWithNonGenericGenerator(CtMethod<?> generatorMethod) {
    CtTypeReference<?> declaringType = generatorMethod.getDeclaringType().getReference();
    CtTypeAccess<?> typeAccess = CodeFactory.createTypeAccess(declaringType);
    Sequence objectSequence = MethodInvocationGenerator.generateMethodInvocation(generatorMethod, typeAccess,
            declaringType, null);
    return objectSequence;
  }

  public static Sequence createObjectWithGenericGenerator(CtTypeReference<?> instanceType,
                                                          CtMethod<?> generatorMethod) {
    InvocationGenerationContext.add(generatorMethod);
    Map<String, CtTypeReference<?>> substitutionRule =
            TypeArgumentSubstitution.inferSubstitutionRule(instanceType, generatorMethod);
    CtTypeReference<?> declaringType = generatorMethod.getDeclaringType().getReference();
    CtTypeAccess<?> typeAccess = CodeFactory.createTypeAccess(declaringType);
    CtInvocation<?> invocation = CodeFactory.createInvocation(generatorMethod, typeAccess);
    List<CtTypeReference<?>> methodTypeArguments = generatorMethod.getFormalCtTypeParameters().stream()
            .map(t -> substitutionRule.get(t.getSimpleName())).collect(Collectors.toList());
    List<VariableType> inputTypes = TypeConcretizer.setupInputTypes(generatorMethod, invocation,
            declaringType, methodTypeArguments);

    CtStatementList result = CodeFactory.createStatementList();
    List<CtExpression<?>> arguments = new ArrayList<>();
    for (VariableType inputType : inputTypes) {
      Sequence argumentCreationSequence = ObjectInstantiator.instantiate(inputType, false);
      if (!argumentCreationSequence.isInlineValue()) {
        for (CtStatement statement : argumentCreationSequence.getCtStatementList()) {
          result.addStatement(statement);
        }
      }
      arguments.add(argumentCreationSequence.getAccessExpression());
    }
    invocation.setArguments(arguments);
    result.addStatement(CodeFactory.createNewLocalVariable(instanceType, invocation));
    InvocationGenerationContext.remove(generatorMethod);
    return new Sequence(result);
  }

  public static Sequence createNullObject(CtTypeReference<?> instanceType) {
    CtLocalVariable<?> nullVariable = CodeFactory.createNullVariable(instanceType);
    return new Sequence(nullVariable);
  }

  public static Sequence createObjectWithGenerator(CtTypeReference<?> instanceType, CtMethod<?> generatorMethod) {
    if (!instanceType.isParameterized()) {
      return createObjectWithNonGenericGenerator(generatorMethod);
    } else {
      return createObjectWithGenericGenerator(instanceType, generatorMethod);
    }
  }

  public static CtStatementList mutateFailedSequenceRandomly(CtStatementList statements, Throwable fault) {
    int maxMutationTargetCount = MutationUtil.maxMutationTargetCount(statements);
    if (maxMutationTargetCount == 0) {
      return null;
    }

    List<CtTypeReference<?>> faultRelatedTypes = fault == null ? null
            : FaultAnalysis.getRelatedTypes(fault.getStackTrace()[0]);

    int mutationCount = RandomUtils.random.nextInt(maxMutationTargetCount) + 1;
    CtStatementList result = statements;
    for (int i = 0; i < mutationCount; i++) {
      List<CtCodeElement> candidateTargets = MutationUtil.getCandidateTargets(result);
      List<CtCodeElement> filteredTargets = faultRelatedTypes == null
              ? candidateTargets
              : candidateTargets.stream().filter(t -> faultRelatedTypes.contains(((CtTypedElement<?>) t).getType()))
              .collect(Collectors.toList());
      CtCodeElement mutationTarget = filteredTargets.isEmpty() ? RandomUtils.select(candidateTargets)
              : RandomUtils.select(filteredTargets);
      CtStatementList mutant;
      if (mutationTarget instanceof CtLocalVariable<?>) {
        mutant = MutationCore.mutateFaultyObject(result, (CtLocalVariable<?>) mutationTarget, faultRelatedTypes);
      } else {
        mutant = MutationCore.mutateExpression(result, (CtExpression<?>) mutationTarget);
      }
      result = mutant == null ? result : mutant;
    }
    return result;
  }

  public static Sequence generateWithNonTypicalGenerator(VariableType inputType) {
    CtTypeReference<?> declType = inputType.getDeclType();
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    List<CtMethod<?>> generatorMethods = CtModelExt.INSTANCE.searchGenerators(declType);
    if (ObjectPool.exploit(inputType)) {
      return ObjectPool.selectExistingObject(inputType);
    }
    Sequence sequence;
    List<CtMethod<?>> nonRecursiveGenerators = generatorMethods.stream()
            .filter(method -> !InvocationGenerationContext.contains(method))
            .filter(method -> method.getParameters().stream()
                    .noneMatch(t -> t.getType().getQualifiedName().equals(inputType.getDeclType().getQualifiedName())))
            .collect(Collectors.toList());
    List<CtMethod<?>> unusedAPIs = nonRecursiveGenerators.stream()
            .filter(e -> !GenerationHistory.hasGeneratorBeenChosen(ExecutableKey.of(e)))
            .collect(Collectors.toList());
    List<CtMethod<?>> candidates = unusedAPIs.isEmpty() ? nonRecursiveGenerators : unusedAPIs;
//    candidates.sort(Comparator.comparingInt(m -> m.getParameters().size()));
    Collections.shuffle(candidates);
    for (CtMethod<?> generatorMethod : candidates) {
      sequence = createObjectWithGenerator(instanceType, generatorMethod);
      GenerationHistory.updateUsedGenerators(generatorMethod);
      if (sequence != null) {
        ObjectPool.put(inputType, sequence);
        return sequence;
      }
    }
    return null;
  }
}
