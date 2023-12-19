package npetest.synthesizer.typeadaption;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.exceptions.SynthesisFailure;
import npetest.commons.filters.InstantiableTypeFilter;
import npetest.commons.filters.TypeParameterReferenceFilter;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.ParameterKey;
import npetest.commons.keys.TypeKey;
import npetest.commons.keys.TypeReferenceKey;
import npetest.commons.logger.LogMessageTemplate;
import npetest.commons.logger.LoggingUtils;
import npetest.commons.misc.RandomUtils;
import npetest.commons.models.ModeledTypeFactory;
import npetest.commons.models.ModeledTypes;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeAccessibilityChecker;
import npetest.commons.spoon.TypeUsabilityChecker;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.search.type.InputTypeSearchSpace;
import npetest.synthesizer.search.type.TypeArgumentSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeInformation;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ClassTypingContext;
import spoon.support.visitor.MethodTypingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeConcretizer {
  private TypeConcretizer() {
  }

  private static final Logger logger = LoggerFactory.getLogger(TypeConcretizer.class);

  private static final Map<ExecutableKey, List<CtType<?>>> mainClassCache = new HashMap<>();

  private static final Set<String> unresolvableGenericTypes = new HashSet<>();

  public static CtType<?> selectConcreteMainClass(CtMethod<?> mut) {
    CtType<?> declaringType = mut.getDeclaringType();
    if (!declaringType.isAbstract()) {
      return declaringType;
    }

    logger.debug("* Selecting concrete main class to test {}...", ExecutableKey.of(mut));

    ExecutableKey mutKey = ExecutableKey.of(mut);
    List<CtType<?>> candidateMainClasses = mainClassCache.get(mutKey);
    if (candidateMainClasses != null) {
      logger.debug("  -> Cached results are found!");
      LoggingUtils.logListDebug(logger, candidateMainClasses, "Cached sub types", CtTypeInformation::getQualifiedName);
    } else {
      logger.debug("  -> Cached results are not found -> Searching...");
      List<CtType<?>> allCandidateClasses = CtModelExt.INSTANCE.findConcreteSubtypes(declaringType);

      /*
       * Consider subset of `allCandidateClasses` that can be used
       * to test the given method.
       * Current filtering criteria:
       * 1. type that doesn't override the method under test
       * 2. type overrides the method, but it can call the method under test with super.
       */
      logger.debug("* Filtering sub classes to test {}...", ExecutableKey.of(mut));
      candidateMainClasses = new ArrayList<>();
      for (CtType<?> subType : allCandidateClasses) {
        List<CtMethod<?>> methodsInSubType = subType.getMethodsByName(mut.getSimpleName());
        boolean overridden = false;
        for (CtMethod<?> method : methodsInSubType) {
          if (method.isOverriding(mut)) {
            overridden = true;
            if (isCallingSuperOverriddenMethod(method, mut)) {
              candidateMainClasses.add(subType);
            }
          }
        }
        if (mut.isAbstract() || !overridden) {
          // The method under test is not overridden by the child class
          candidateMainClasses.add(subType);
        }
      }
      LoggingUtils.logListDebug(logger, candidateMainClasses, "Filtered sub types",
              CtTypeInformation::getQualifiedName);
      mainClassCache.put(mutKey, candidateMainClasses);
    }


    if (candidateMainClasses.isEmpty()) {
      List<CtMethod<?>> generators = CtModelExt.INSTANCE.searchGenerators(declaringType.getReference());
      return generators.isEmpty() ? null : declaringType;
    }

    CtType<?> select = RandomUtils.select(candidateMainClasses);
    logger.debug("  -> Selected: {}", TypeKey.of(select));
    return select;
  }

  private static boolean isCallingSuperOverriddenMethod(CtMethod<?> overridingMethod, CtMethod<?> targetMethod) {
    return !overridingMethod.filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> invocation) ->
                    invocation.getTarget() instanceof CtSuperAccess &&
                            ExecutableKey.of(invocation.getExecutable()).equals(ExecutableKey.of(targetMethod)))
            .list().isEmpty();
  }

  /**
   * Fill in actual type parameters of generic type.
   * This assumes passed type is concrete type.
   *
   * @param mainClass main type declaration that might be generic, but neither interface nor abstract class.
   * @return a reference of <code>mainClass</code>, filled with
   * actual type parameters.
   */
  public static CtTypeReference<?> concretizeReceiverObjectType(CtType<?> mainClass) {
    if (!mainClass.isGenerics()) {
      return mainClass.getReference();
    }

    List<CtTypeReference<?>> actualInstanceTypeCandidates;
    String mainClassName = mainClass.getQualifiedName();

    if (unresolvableGenericTypes.contains(mainClassName)) {
      // In case where the resolution of generic type has already failed once.
      throw new SynthesisFailure("Failed to concretize generic main class: " + mainClassName);
    }

    /* search instance type for receiver object */
    actualInstanceTypeCandidates = searchTypeArgumentsFromCodeBase(mainClass);

    if (!actualInstanceTypeCandidates.isEmpty() && RandomUtils.p() < 0.5) {
      return RandomUtils.select(actualInstanceTypeCandidates);
    } else {
      return concretizeGenericReceiverObjectType(mainClass);
    }
  }

  public static CtTypeReference<?> adaptParameterType(CtExecutable<?> executable, int i,
                                                      CtTypeReference<?> receiverObjectType) {
    CtTypeReference<?> paramType = preprocessParamType(executable, i);
    ParameterKey parameterKey = ParameterKey.of(executable, i);
    logger.debug("  {}. parameter {}", i + 1, parameterKey);
    List<CtTypeReference<?>> candidateAdaptedParameterTypes = new ArrayList<>();
    if (!(executable instanceof CtConstructor<?>)
            && !((CtFormalTypeDeclarer) executable).getFormalCtTypeParameters().isEmpty()
            && paramType.isGenerics()) {
      candidateAdaptedParameterTypes.add(paramType.clone());
    } else {
      /* Recursively resolve type, considering the possibility that it is array type */
      candidateAdaptedParameterTypes.add(adaptParameterTypeLoop(paramType, receiverObjectType));
    }

    LoggingUtils.logListDebug(logger, candidateAdaptedParameterTypes, "Candidate adapted parameter types",
            CtTypeReference::toString);

    CtTypeReference<?> select = RandomUtils.select(candidateAdaptedParameterTypes);
    logger.debug("  Selected: {}", select);
    return select;
  }

  private static CtTypeReference<?> adaptParameterTypeLoop(CtTypeReference<?> paramType,
                                                           CtTypeReference<?> baseObjectType) {
    CtTypeReference<?> result;
    if (paramType.isArray()) {
      CtArrayTypeReference<?> arrayParamType = (CtArrayTypeReference<?>) paramType;
      CtTypeReference<?> componentType = arrayParamType.getComponentType();
      if (componentType.isArray()) {
        logger.debug("  Multi-dimensional array is not supported: {}", paramType);
        result = null;
      } else {
        /* ClassTypingContext.adaptType doesn't work for generic array */
        CtTypeReference<?> adaptedComponentType = adaptParameterTypeLoop(componentType, baseObjectType);
        result = arrayParamType.clone().setComponentType(adaptedComponentType);
      }
    } else {
      CtTypeReference<?> adaptedType = adaptTypeWithBaseObject(paramType, baseObjectType);
      if (adaptedType != null) {
        if (TypeUtils.isJavaLangEnum(adaptedType)) {
          result = concretizeEnumType(adaptedType);
        } else {
          result = adaptedType;
        }
      } else {
        result = conductManualTypeAdaption(paramType.getTypeDeclaration(), baseObjectType);
      }
    }
    return result;
  }

  private static CtTypeReference<?> concretizeEnumType(CtTypeReference<?> enumType) {
    CtTypeReference<?> actualTypeArgument = enumType.getActualTypeArguments().get(0);
    CtTypeReference<?> result;
    if (actualTypeArgument instanceof CtWildcardReference) {
      List<CtTypeReference<?>> candidateEnums = CtModelExt.INSTANCE.getAllEnumTypeReferences();
      result = RandomUtils.select(candidateEnums);
    } else {
      assert actualTypeArgument.isEnum();
      result = actualTypeArgument.clone();
    }
    return result;
  }

  private static List<CtTypeReference<?>> concretizeWildcards(CtTypeReference<?> declarationType) {
    logger.debug("  Concretizing wildcards of {}...", declarationType);
    List<Integer> wildcardIndices = TypeUtils.getWildcardIndices(declarationType);

    List<CtTypeReference<?>> results;
    if (wildcardIndices.isEmpty() || declarationType.isArray()) {
      // Use wildcard for generic array declaration
      results = new ArrayList<>(Arrays.asList(declarationType));
    } else {
      results = TypeArgumentSubstitution.enumerateMinedTypeParameters(declarationType, wildcardIndices);
      if (results.isEmpty()) {
        results = TypeArgumentSubstitution.fillWildcardsWithRandomTypes(declarationType);
      }
    }
    return results;
  }

  public static VariableType concretizeActualInstanceType(CtTypeReference<?> declarationType,
                                                          CtExecutable<?> executable,
                                                          CtTypeReference<?> baseObjectType,
                                                          int i) {
    boolean isPrimitive = declarationType.isPrimitive();
    List<CtTypeReference<?>> concreteDeclTypeCandidates = concretizeWildcards(declarationType);
    VariableType result;
    if (concreteDeclTypeCandidates.isEmpty()) {
      result = VariableType.fromFailedAdaption(declarationType);
    } else {
      List<VariableType> instanceTypeCandidates = new ArrayList<>();
      int parameterSpace = 0;
      for (CtTypeReference<?> concreteDeclType : concreteDeclTypeCandidates) {
        CtTypeReference<?> instanceType = TypeArgumentSubstitution.fillWildcardsWithBoundingType(concreteDeclType);
        List<CtTypeReference<?>> concreteInstanceTypes = findConcreteInstanceType(instanceType, executable, baseObjectType);
        // consider null type
        parameterSpace += concreteInstanceTypes.size();
        for (CtTypeReference<?> concreteInstanceType : concreteInstanceTypes) {
          VariableType instanceTypeCandidate = concreteInstanceType != null
                  ? VariableType.fromSuccessfulAdaption(concreteDeclType, concreteInstanceType)
                  : VariableType.fromFailedAdaption(concreteDeclType);
          instanceTypeCandidates.add(instanceTypeCandidate);
        }
      }
      parameterSpace += !isPrimitive ? 1 : 0;
      InvocationGenerationContext.updateParameterSearchSpace(executable, i, parameterSpace);
      result = RandomUtils.select(instanceTypeCandidates);
    }

    return result == null ? VariableType.fromFailedAdaption(declarationType) : result;
  }

  private static List<CtTypeReference<?>> findConcreteInstanceType(CtTypeReference<?> instanceType,
                                                                   CtExecutable<?> executable,
                                                                   CtTypeReference<?> baseObjectType) {
    logger.debug("  Finding actual instance type of {}...", instanceType);
    List<CtTypeReference<?>> actualInstanceTypes = new ArrayList<>();
    if (TypeUtils.isJavaLangObject(instanceType)) {
      actualInstanceTypes.add(findProperTypeForObjectType(instanceType, executable, baseObjectType));
    } else if (TypeUtils.isJavaLangClass(instanceType)) {
      CtTypeReference<?> typeReference = instanceType.getActualTypeArguments().get(0);
      typeReference.setActualTypeArguments(new ArrayList<>());
      actualInstanceTypes.add(instanceType.clone());
    } else if (ModeledTypes.hasModeledSubtype(instanceType)) {
      actualInstanceTypes.add(ModeledTypeFactory.create(instanceType).getAdaptedType());
    } else if (instanceType.isEnum()) {
      actualInstanceTypes.add(instanceType);
    } else if (TypeUtils.isPrimitive(instanceType)) {
      actualInstanceTypes.add(instanceType);
    } else if (TypeUtils.isString(instanceType)) {
      actualInstanceTypes.add(instanceType.unbox());
    } else if (TypeUtils.isBoxingType(instanceType)) {
      actualInstanceTypes.add(instanceType);
    } else if (TypeUtils.isPrimitiveArray(instanceType)) {
      actualInstanceTypes.add(instanceType);
    } else if (instanceType.isArray()) {
      // Non-primitive array type (1-dimensional)
      actualInstanceTypes.add(resolveInstanceTypeOfArray((CtArrayTypeReference<?>) instanceType));
    } else if (instanceType.getTypeDeclaration().isAbstract() || instanceType.getTypeDeclaration().isInterface()) {
      /* abstract or interface type of which type parameters are all resolved */
      List<CtTypeReference<?>> candidateSubtypes = CtModelExt.INSTANCE.findCandidateConcreteSubtypes(instanceType);
      if (candidateSubtypes.isEmpty()) {
        logger.debug("  : Failed to find concrete subtype of {}", instanceType);
      } else {
        List<CtTypeReference<?>> candidateConcreteParameterTypes =
                resolveGenericTypesWithEnumeratingAllPossibleSubstitutions(candidateSubtypes);
        actualInstanceTypes.addAll(candidateConcreteParameterTypes);
      }
    } else {
      actualInstanceTypes.add(instanceType);
    }
    return actualInstanceTypes;
  }

  private static CtTypeReference<?> resolveInstanceTypeOfArray(CtArrayTypeReference<?> instanceType) {
    CtTypeReference<?> actualInstanceType = null;
    CtTypeReference<?> componentType = instanceType.getComponentType();
    CtType<?> componentTypeDecl = componentType.getTypeDeclaration();
    if (componentTypeDecl.isAbstract() || componentTypeDecl.isInterface()) {
      // No need to fill type arguments since generic array is not allowed
      List<CtType<?>> componentSubtypes = CtModelExt.INSTANCE.findConcreteSubtypes(componentTypeDecl);
      if (componentSubtypes.isEmpty()) {
        logger.debug("  : Failed to find concrete subtype of {}", componentType);
      } else {
        CtTypeReference<?> componentTypeReference = RandomUtils.select(componentSubtypes).getReference();
        actualInstanceType = instanceType.clone().setComponentType(componentTypeReference);
      }
    } else {
      return instanceType;
    }
    return actualInstanceType;
  }

  private static CtTypeReference<?> findProperTypeForObjectType(CtTypeReference<?> concreteDeclType,
                                                                CtExecutable<?> executable,
                                                                CtTypeReference<?> baseObjectType) {
    CtTypeReference<?> actualInstanceType;
    if (baseObjectType.isParameterized()) {
      /* This parameter might be supposed to be of same type with resolved type parameter.
       * e.g. `java.util.Map.containsKey(java.lang.Object)` should take the
       * input of `K` type, declared in `Map<K, V>`.
       */
      List<CtTypeReference<?>> candidates = new ArrayList<>();
      List<CtTypeParameter> formalCtTypeParameters =
              ((CtFormalTypeDeclarer) executable).getDeclaringType().getFormalCtTypeParameters();
      if (formalCtTypeParameters.stream().anyMatch(t1 -> executable.getParameters().stream()
              .anyMatch(p -> p.getReferencedTypes().stream()
                      .anyMatch(t2 -> t1.getSimpleName().equals(t2.getSimpleName()))))) {
        candidates.addAll(formalCtTypeParameters.stream()
                .map(new ClassTypingContext(baseObjectType)::adaptType)
                .collect(Collectors.toList()));
        actualInstanceType = RandomUtils.select(candidates);
      } else {
        actualInstanceType = concreteDeclType;
      }
    } else {
      actualInstanceType = concreteDeclType;
    }
    return actualInstanceType;
  }

  public static List<CtTypeReference<?>> resolveGenericTypesWithEnumeratingAllPossibleSubstitutions(
          List<CtTypeReference<?>> mightBeGenericTypes) {
    List<CtTypeReference<?>> concreteTypeReferences = new ArrayList<>();
    List<CtTypeReference<?>> filteredTypes = mightBeGenericTypes.stream()
            .filter(InstantiableTypeFilter.INSTANCE.setPackage(TestGenContext.getCtPackage()))
            .collect(Collectors.toList());
    for (CtTypeReference<?> genericType : filteredTypes) {
      if (genericType.isGenerics()) {
        int typeParameterCount = genericType.getElements(new TypeFilter<>(CtTypeParameterReference.class)).size();
        List<Map<Integer, CtTypeReference<?>>> substitutionRules =
                TypeArgumentSubstitution.generateSubstitutionRules(typeParameterCount,
                        TypeUtils.defaultTypeParameters());
        for (Map<Integer, CtTypeReference<?>> substitutionRule : substitutionRules) {
          CtTypeReference<?> result = genericType.clone();
          resolveTypeParameterReferencesWithSubstitution(result, substitutionRule);
          concreteTypeReferences.add(result);
        }
      } else {
        concreteTypeReferences.add(genericType);
      }
    }
    return concreteTypeReferences;
  }

  public static CtTypeReference<?> substituteMethodTypeParameters(CtTypeReference<?> adaptedParameterType,
                                                                  Map<String, CtTypeReference<?>> substitutionRule) {
    CtTypeReference<?> resolvedType = adaptedParameterType;
    Set<String> formalTypeParameters = substitutionRule.keySet();
    for (String formalTypeParameter : formalTypeParameters) {
      CtTypeReference<?> substitute = substitutionRule.get(formalTypeParameter).clone();
      List<CtTypeParameterReference> typeParameterReferences = resolvedType
              .filterChildren(new TypeFilter<>(CtTypeParameterReference.class))
              .select(typeParam -> typeParam.toString().equals(formalTypeParameter))
              .list();
      boolean itself = false;
      for (int i = 0; i < typeParameterReferences.size(); i++) {
        CtTypeParameterReference typeParameterReference = typeParameterReferences.get(i);
        if (typeParameterReference.equals(resolvedType)) {
          itself = true;
          break;
        }
        typeParameterReference.replace(substitute);
      }
      if (itself) {
        resolvedType = substitute;
      }
    }
    logger.debug(LogMessageTemplate.TRANSITION_TO, resolvedType);
    return resolvedType;
  }

  public static List<CtTypeReference<?>> setupTypeArgumentsOfExecutable(CtExecutable<?> executable) {
    return ((CtFormalTypeDeclarer) executable).getFormalCtTypeParameters().isEmpty()
            ? new ArrayList<>()
            : TypeArgumentSearcher.getRandomActualMethodTypeParameters(executable);
  }

  private static CtTypeReference<?> adaptTypeWithBaseObject(CtTypeReference<?> targetType,
                                                            CtTypeReference<?> baseObjectType) {
    if (targetType instanceof CtTypeParameterReference) {
      CtTypeReference<?> adaptedType = TypeAdapter.adaptType(targetType, baseObjectType);
      return adaptedType == null ? TypeUtils.objectType().clone() : adaptedType;
    } else if (TypeUtils.isJavaLangObject(targetType) &&
            baseObjectType.getTypeDeclaration().isGenerics()) {
      List<CtTypeReference<?>> candidateTypesForJavaLangObject =
              ObjectTypeReplacement.getCandidateTypesForJavaLangObject(baseObjectType);
      List<CtTypeReference<?>> filteredCandidates = candidateTypesForJavaLangObject.stream().filter(t ->
              TypeUsabilityChecker.isDeclarable(t, t.isArray())).collect(Collectors.toList());
      if (RandomUtils.p() < 0.5 && !filteredCandidates.isEmpty()) {
        return RandomUtils.select(filteredCandidates);
      }
    }
    List<CtTypeParameterReference> typeParameterReferences =
            targetType.getElements(TypeParameterReferenceFilter.INSTANCE);
    for (int i = 0; i < typeParameterReferences.size(); i++) {
      CtTypeParameterReference typeParameterReference = typeParameterReferences.get(i);
      CtTypeReference<?> adaptedTypeParameterReference = TypeAdapter.adaptType(typeParameterReference, baseObjectType);
      if (adaptedTypeParameterReference != null) {
        typeParameterReference.replace(adaptedTypeParameterReference);
      } else {
        typeParameterReference.replace(TypeUtils.wildcard().clone());
      }
    }
    return targetType;
  }

  public static CtTypeReference<?> preprocessParamType(CtExecutable<?> executable, int pos) {
    CtParameter<?> ctParameter = executable.getParameters().get(pos);
    CtTypeReference<?> paramType = ctParameter.getType().clone();

    if (ctParameter.isVarArgs()) {
      return paramType;
    }

    /*
     * Cloned CtTypeReference for parameter type should be child of CtParameter.
     * This is to avoid error from ClassTypingContext#adaptType.
     */
    paramType.setParent(ctParameter);

    if (TypeUtils.isRawType(paramType)) {
      return TypeUtils.parameterizedTypeOfRawType(paramType);
    } else {
      return paramType;
    }
  }

  private static List<CtTypeReference<?>> searchTypeArgumentsFromCodeBase(CtType<?> genericType) {
    List<CtTypeReference<?>> completeInstanceTypeCandidates = new ArrayList<>();
    completeInstanceTypeCandidates.addAll(CtModelExt.INSTANCE.gatherActualTypesFromDeclarations(genericType));
    completeInstanceTypeCandidates.addAll(CtModelExt.INSTANCE.gatherActualTypesFromConstructorCall(genericType));
    return completeInstanceTypeCandidates.stream()
            .filter(typeReference -> typeReference.filterChildren(new TypeFilter<>(CtTypeReference.class)).list().stream()
                    .map(t -> (CtTypeReference<?>) t)
                    .allMatch(t -> TypeAccessibilityChecker.isPackageLevelAccessible(t.getTypeDeclaration(), TestGenContext.getCtPackage())))
            .collect(Collectors.toList());
  }

  private static CtTypeReference<?> concretizeGenericReceiverObjectType(CtType<?> genericType) {
    // Assign wildcards to actual type parameters as placeholder.
    List<CtTypeReference<?>> prospectiveWildcards = resolveBoundedTypeParameters(genericType);
    List<CtTypeReference<?>> wildcards = new ArrayList<>();
    for (CtTypeReference<?> prospectiveWildcard : prospectiveWildcards) {
      if (prospectiveWildcard instanceof CtWildcardReference) {
        wildcards.add(prospectiveWildcard);
      } else if (prospectiveWildcard instanceof CtTypeParameterReference) {
        CtWildcardReference wildcard = CodeFactory.createWildcardReference();
        wildcards.add(wildcard);
      } else {
        // resolved wildcard
        wildcards.add(prospectiveWildcard);
      }
    }

    // Enumerate all possible combinations of prepared types.
    CtTypeReference<?> genericTypeWithWildcards = genericType.getReference().setActualTypeArguments(wildcards);
    List<CtTypeReference<?>> concreteReceiverTypes =
            TypeArgumentSubstitution.getAllPossibleTypeArgumentsCombination(genericTypeWithWildcards);
    InputTypeSearchSpace.receiverTypes.put(genericTypeWithWildcards.getQualifiedName(), concreteReceiverTypes);
    return RandomUtils.select(concreteReceiverTypes);
  }

  private static List<CtTypeReference<?>> resolveBoundedTypeParameters(CtType<?> genericType) {
    List<CtTypeReference<?>> boundingTypes = new ArrayList<>();
    for (CtTypeParameter formalCtTypeParameter : genericType.getFormalCtTypeParameters()) {
      if (formalCtTypeParameter.getSuperclass() != null) {
        CtTypeReference<?> superclass = formalCtTypeParameter.getSuperclass();
        List<CtTypeParameterReference> selfReferringTypeParameters =
                superclass.filterChildren(new TypeFilter<>(CtTypeParameterReference.class))
                        .select(t -> t.toString().equals(formalCtTypeParameter.getQualifiedName())).list();
        if (selfReferringTypeParameters.isEmpty()) {
          boundingTypes.add(formalCtTypeParameter.getSuperclass());
        } else {
          CtTypeReference<?> boundingType = TypeArgumentSubstitution.fillSelfReferringSubtypes(superclass);
          if (boundingType.isInterface() ||
                  (boundingType.getTypeDeclaration() != null && boundingType.getTypeDeclaration().isAbstract())) {
            List<CtTypeReference<?>> subtypes = CtModelExt.INSTANCE.findCandidateConcreteSubtypes(boundingType);
            boundingTypes.add(RandomUtils.select(subtypes));
          } else {
            boundingTypes.add(boundingType);
          }
        }
      } else {
        boundingTypes.add(formalCtTypeParameter.getReference());
      }
    }
    return boundingTypes;
  }

  public static void resolveTypeParameterReferencesWithSubstitution(CtTypeReference<?> typeReference,
                                                                    Map<Integer, CtTypeReference<?>> substitutionRule) {
    List<CtTypeParameterReference> typeParameterReferences =
            typeReference.getElements(new TypeFilter<>(CtTypeParameterReference.class));
    for (int i = 0; i < typeParameterReferences.size(); i++) {
      CtTypeParameterReference typeParameterReference = typeParameterReferences.get(i);
      typeParameterReference.replace(substitutionRule.get(i).clone());
    }
  }

  public static Map<String, CtTypeReference<?>> constructSubstitutionRule(CtExecutable<?> executable,
                                                                          List<CtTypeReference<?>> actualMethodTypeParameters) {
    if (executable instanceof CtConstructor<?>) {
      return new HashMap<>();
    }
    CtMethod<?> method = (CtMethod<?>) executable;
    Map<String, CtTypeReference<?>> substitutionRule = new HashMap<>();
    List<CtTypeParameter> formalTypeParameters = method.getFormalCtTypeParameters();
    for (int i = 0; i < actualMethodTypeParameters.size(); i++) {
      CtTypeParameter ctTypeParameter = formalTypeParameters.get(i);
      CtTypeReference<?> substitution = actualMethodTypeParameters.get(i);
      substitutionRule.put(ctTypeParameter.getSimpleName(), substitution);
    }
    return substitutionRule;
  }

  public static CtTypeReference<?> concretizeReturnTypeOfInvocation(CtInvocation<?> invocation,
                                                                    CtMethod<?> method,
                                                                    CtTypeReference<?> instanceType) {
    if (!method.getType().isGenerics()) {
      if (TypeUsabilityChecker.isDeclarable(method.getType(), method.getType().isArray())) {
        return method.getType();
      } else {
        return null;
      }
    }

    /*
     * Current Spoon version does not support the case where `instanceType == null`.
     */
    ClassTypingContext classTypingContext = instanceType != null
            ? new ClassTypingContext(instanceType) : new ClassTypingContext(method.getDeclaringType());
    List<CtTypeReference<?>> accessibleReturnTypeCandidates = TypeUtils.getReferableSubtypes(method.getType());
    CtTypeReference<?> returnType = null;
    for (CtTypeReference<?> accessibleReturnTypeCandidate : accessibleReturnTypeCandidates) {
      try {
        returnType = new MethodTypingContext()
                .setClassTypingContext(classTypingContext)
                .setInvocation(invocation)
                .adaptType(accessibleReturnTypeCandidate);
        if (returnType != null) {
          break;
        }
      } catch (Exception e) {
        logger.debug("Failed to concretize return type of {} for receiver of {} using MethodTypingContext",
                ExecutableKey.of(method), TypeReferenceKey.of(instanceType));
      }
    }
    if (returnType == null || returnType.isGenerics()) {
      if (instanceType == null) {
        logger.debug("Failed to concretize return type of {}", ExecutableKey.of(method));
      } else {
        logger.debug("Failed to concretize return type of {} for receiver of {}",
                ExecutableKey.of(method), TypeReferenceKey.of(instanceType));
      }
      returnType = TypeUtils.parameterizedTypeOfRawType(ASTUtils.getReturnType(method));
    }
    return returnType;
  }

  public static List<VariableType> setupInputTypes(CtExecutable<?> executable,
                                                   CtAbstractInvocation<?> invocation,
                                                   CtTypeReference<?> baseObjectType,
                                                   List<CtTypeReference<?>> methodTypeArguments) {
    logger.debug("* Resolving input types of {} with base object type {}",
            ExecutableKey.of(executable), baseObjectType);

    invocation.setValueByRole(CtRole.TYPE_ARGUMENT, methodTypeArguments);
    Map<String, CtTypeReference<?>> substitutionRule = constructSubstitutionRule(executable, methodTypeArguments);
    List<VariableType> variableTypes = new ArrayList<>();

    int n = executable.getParameters().size();
    for (int i = 0; i < n; i++) {
      CtTypeReference<?> declarationType = createDeclarationType(executable, i, baseObjectType, substitutionRule);
      VariableType variableType = declarationType == null
              ? VariableType.fromFailedAdaption(preprocessParamType(executable, i))
              : createVariableType(declarationType, executable, baseObjectType, i);
      variableTypes.add(variableType);
    }
    return variableTypes;
  }

  private static VariableType createVariableType(CtTypeReference<?> declarationType, CtExecutable<?> executable,
                                                 CtTypeReference<?> baseObjectType, int i) {
    return concretizeActualInstanceType(declarationType, executable, baseObjectType, i);
  }

  private static CtTypeReference<?> createDeclarationType(CtExecutable<?> executable, int i,
                                                          CtTypeReference<?> baseObjectType,
                                                          Map<String, CtTypeReference<?>> substitutionRule) {
    CtTypeReference<?> adaptedParameterType = adaptParameterType(executable, i, baseObjectType);
    if (adaptedParameterType == null) {
      return null;
    }

    if (TypeUtils.isRawType(adaptedParameterType)) {
      List<CtTypeReference<?>> randomActualTypeArguments =
              TypeArgumentSearcher.getRandomActualTypeArguments(adaptedParameterType);
      return adaptedParameterType.setActualTypeArguments(randomActualTypeArguments);
    }
    return substituteMethodTypeParameters(adaptedParameterType, substitutionRule);
  }

  public static CtTypeReference<?> adaptTypeParameterOfFactoryMethod(CtType<?> factoryType,
                                                                     CtTypeReference<?> instanceType) {
    ClassTypingContext classTypingContext = new ClassTypingContext(instanceType);
    List<CtTypeReference<?>> actualTypeParameterReferences = new ArrayList<>();
    boolean failure = false;
    for (CtTypeParameter formalCtTypeParameter : factoryType.getFormalCtTypeParameters()) {
      CtTypeReference<?> adaptedTypeParameter = classTypingContext.adaptType(formalCtTypeParameter);
      if (adaptedTypeParameter == null || adaptedTypeParameter.isGenerics()) {
        failure = true;
        break;
      } else {
        actualTypeParameterReferences.add(adaptedTypeParameter);
      }
    }

    return failure ? conductManualTypeAdaption(factoryType, instanceType) :
            factoryType.getReference().setActualTypeArguments(actualTypeParameterReferences);
  }

  private static CtTypeReference<?> conductManualTypeAdaption(CtType<?> target, CtTypeReference<?> instanceType) {
    Map<String, CtTypeReference<?>> substitutionRules = extractSubstitutionRule(instanceType);
    if (target instanceof CtTypeParameter) {
      return substitutionRules.get(target.getSimpleName());
    } else {
      List<CtTypeReference<?>> actualTypeParameterReferences = new ArrayList<>();
      for (CtTypeParameter formalCtTypeParameter : target.getFormalCtTypeParameters()) {
        actualTypeParameterReferences.add(substitutionRules.get(formalCtTypeParameter.getSimpleName()));
      }
      return target.getReference().setActualTypeArguments(actualTypeParameterReferences);
    }
  }

  private static Map<String, CtTypeReference<?>> extractSubstitutionRule(CtTypeReference<?> instanceType) {
    List<CtTypeParameter> formalCtTypeParameters = instanceType.getTypeDeclaration().getFormalCtTypeParameters();
    List<CtTypeReference<?>> actualTypeArguments = instanceType.getActualTypeArguments();
    Map<String, CtTypeReference<?>> substitutionRule = new HashMap<>();
    for (int i = 0; i < formalCtTypeParameters.size(); i++) {
      substitutionRule.put(formalCtTypeParameters.get(i).getSimpleName(), actualTypeArguments.get(i).clone());
    }
    return substitutionRule;
  }
}
