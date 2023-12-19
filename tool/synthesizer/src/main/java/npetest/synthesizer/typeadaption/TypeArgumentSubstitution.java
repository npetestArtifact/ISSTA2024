package npetest.synthesizer.typeadaption;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUsabilityChecker;
import npetest.commons.spoon.TypeUtils;
import npetest.synthesizer.context.TestGenContext;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.chain.CtQueryable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeArgumentSubstitution {
  private static final Map<String, List<CtTypeReference<?>>> resolvedGenericTypeExamples = new HashMap<>();

  private TypeArgumentSubstitution() {
  }

  public static Collection<Map<Integer, CtTypeReference<?>>> gatherSubstitutionRulesWithMining(CtTypeReference<?> declarationType,
                                                                                               List<Integer> wildcardIndices) {
    boolean isInterface = declarationType.isInterface();
    CtType<?> declarationCtType = declarationType.getTypeDeclaration();
    boolean isAbstract = declarationCtType.isAbstract();
    List<Map<Integer, CtTypeReference<?>>> substitutionRules = new ArrayList<>();
    if (isInterface || isAbstract) {
      List<CtType<?>> directSubtypes = CtModelExt.INSTANCE.findDirectSubtypes(declarationCtType)
              .stream().filter(t -> !t.isShadow() && !t.isAnonymous()).collect(Collectors.toList());
      for (CtType<?> subType : directSubtypes) {
        if (isAbstract) {
          substitutionRules.add(createSubstitutionRuleFromSuperclass(wildcardIndices, subType));
        } else {
          substitutionRules.addAll(createSubstitutionRuleFromSuperInterface(wildcardIndices, subType, declarationType));
        }
      }
    }

    return new HashSet<>(substitutionRules);
  }

  private static Map<Integer, CtTypeReference<?>> createSubstitutionRuleFromSuperclass(List<Integer> wildcardIndices,
                                                                                       CtType<?> subType) {
    CtTypeReference<?> superclass = subType.getSuperclass();
    return createSubstitutionRule(wildcardIndices, superclass);
  }

  private static List<Map<Integer, CtTypeReference<?>>> createSubstitutionRuleFromSuperInterface(List<Integer> wildcardIndices,
                                                                                                 CtType<?> subType,
                                                                                                 CtTypeReference<?> superType) {
    List<Map<Integer, CtTypeReference<?>>> substitutionRules = new ArrayList<>();
    Set<CtTypeReference<?>> superInterfaces = subType.getSuperInterfaces();
    for (CtTypeReference<?> superInterface : superInterfaces) {
      if (!superInterface.getQualifiedName().equals(superType.getQualifiedName())) {
        continue;
      }
      Map<Integer, CtTypeReference<?>> substitutionRule = createSubstitutionRule(wildcardIndices, superInterface);
      if (substitutionRule != null) {
        substitutionRules.add(substitutionRule);
      }
    }
    return substitutionRules;
  }

  private static Map<Integer, CtTypeReference<?>> createSubstitutionRule(List<Integer> wildcardIndices,
                                                                         CtTypeReference<?> typeReference) {
    Map<Integer, CtTypeReference<?>> substitutionRule = new HashMap<>();
    int i = 0;
    boolean fail = false;
    for (CtTypeReference<?> actualTypeArgument : typeReference.getActualTypeArguments()) {
      if (wildcardIndices.contains(i) && TypeUsabilityChecker.isDeclarable(actualTypeArgument, false)) {
        substitutionRule.put(i, actualTypeArgument);
      } else {
        fail = true;
        break;
      }
      i++;
    }

    return fail ? null : substitutionRule;
  }

  public static List<CtTypeReference<?>> enumerateMinedTypeParameters(CtTypeReference<?> typeReference,
                                                                      List<Integer> wildcardIndices) {
    Collection<Map<Integer, CtTypeReference<?>>> substitutionRules =
            gatherSubstitutionRulesWithMining(typeReference, wildcardIndices);
    return enumerateWildcardSubstitutionRules(typeReference, substitutionRules);
  }

  public static List<Map<Integer, CtTypeReference<?>>> generateSubstitutionRules(
          int targetCount, List<CtTypeReference<?>> candidates) {
    int candidateSize = candidates.size();
    int wholeSize = (int) Math.pow(candidateSize, targetCount);
    List<Map<Integer, CtTypeReference<?>>> substitutions = new ArrayList<>();
    for (int i = 0; i < wholeSize; i++) {
      substitutions.add(new HashMap<>());
    }

    int repeat = wholeSize / candidateSize;
    for (int pos = 0; pos < targetCount; pos++) {
      for (int i = 0; i < wholeSize; i++) {
        Map<Integer, CtTypeReference<?>> map = substitutions.get(i);
        CtTypeReference<?> ctTypeReference = candidates.get((i / repeat) % candidateSize).clone();
        map.put(pos, ctTypeReference);
      }
      repeat /= candidateSize;
    }
    return substitutions;
  }

  public static List<CtTypeReference<?>> enumerateWildcardSubstitutionRules(
          CtTypeReference<?> declarationType, Collection<Map<Integer, CtTypeReference<?>>> substitutionRules) {
    List<CtTypeReference<?>> results = new ArrayList<>();
    for (Map<Integer, CtTypeReference<?>> substitutionRule : substitutionRules) {
      CtTypeReference<?> clonedTypeReference = declarationType.clone();
      replaceWildcards(clonedTypeReference, substitutionRule);
      results.add(clonedTypeReference);
    }
    return results;
  }

  private static void replaceWildcards(CtTypeReference<?> typeReference,
                                       Map<Integer, CtTypeReference<?>> substitutionRule) {
    List<CtWildcardReference> wildcards = TypeUtils.getWildcardArguments(typeReference);
    for (int i = 0; i < wildcards.size(); i++) {
      CtWildcardReference wildcard = wildcards.get(i);
      wildcard.replace(substitutionRule.get(i).clone());
    }
  }

  public static CtTypeReference<?> fillWildcardsWithBoundingType(CtTypeReference<?> typeReference) {
    CtTypeReference<?> result = typeReference.clone();
    List<CtWildcardReference> wildcards = result.getElements(new TypeFilter<>(CtWildcardReference.class));
    for (int i = 0; i < wildcards.size(); i++) {
      CtWildcardReference wildcard = wildcards.get(i);
      CtTypeReference<?> boundingType = wildcard.getBoundingType();
      if (boundingType != null && !boundingType.isImplicit()) {
        CtTypeReference<?> substitution = chooseWildcardSubstitutionType(wildcard, boundingType);
        wildcard.replace(substitution);
      }
    }
    return result;
  }

  private static CtTypeReference<?> chooseWildcardSubstitutionType(CtWildcardReference wildcard,
                                                                   CtTypeReference<?> boundingType) {
    CtTypeReference<?> substitution;
    if (wildcard.isUpper()) {
      if (!(boundingType instanceof CtTypeParameterReference)) {
        List<CtType<?>> concreteSubtypes = CtModelExt.INSTANCE.findConcreteSubtypes(boundingType.getTypeDeclaration());
        substitution = !concreteSubtypes.isEmpty()
                ? Objects.requireNonNull(RandomUtils.select(concreteSubtypes)).getReference() : boundingType;
      } else {
        substitution = ((CtTypeParameterReference) boundingType).getTypeDeclaration().getReference();
      }
    } else {
      substitution = ((CtTypeParameterReference) boundingType).getTypeDeclaration().getReference();
    }
    return substitution;
  }

  public static Map<String, CtTypeReference<?>> inferSubstitutionRule(CtTypeReference<?> instanceType,
                                                                      CtMethod<?> generatorMethod) {
    CtTypeReference<?> returnType = generatorMethod.getType();
    Map<String, CtTypeReference<?>> substitutionRule = new HashMap<>();
    for (int i = 0; i < instanceType.getActualTypeArguments().size(); i++) {
      CtTypeReference<?> typeParameter = returnType.getActualTypeArguments().get(i);
      CtTypeReference<?> typeArgument = instanceType.getActualTypeArguments().get(i);
      substitutionRule.put(typeParameter.getSimpleName(), typeArgument);
    }
    return substitutionRule;
  }

  public static Map<String, CtTypeReference<?>> inferSubstitutionRule(CtTypeReference<?> typeReference) {
    CtType<?> typeDeclaration = typeReference.getTypeDeclaration();
    List<CtTypeReference<?>> actualTypeArguments = typeReference.getActualTypeArguments();
    List<CtTypeParameter> formalCtTypeParameters = typeDeclaration.getFormalCtTypeParameters();
    if (actualTypeArguments.size() != formalCtTypeParameters.size()) {
      return Collections.emptyMap();
    }

    Map<String, CtTypeReference<?>> substitutionRule = new HashMap<>();
    for (int i = 0; i < actualTypeArguments.size(); i++) {
      substitutionRule.put(formalCtTypeParameters.get(i).getSimpleName(), actualTypeArguments.get(i));
    }

    return substitutionRule;
  }

  public static List<CtTypeReference<?>> getAllPossibleTypeArgumentsCombination(CtTypeReference<?> declarationType) {
    CtTypeReference<?> result = declarationType.clone();
    List<CtWildcardReference> wildcardArguments = TypeUtils.getWildcardArguments(result);
    if (wildcardArguments.isEmpty()) {
      return Collections.singletonList(declarationType.clone());
    }

    List<CtTypeReference<?>> typeReferences = new ArrayList<>(TypeUtils.defaultTypeParameters());
    Set<CtTypeReference<?>> allInterfaces = CtModelExt.INSTANCE.getInterfacesFromClass(declarationType.getTypeDeclaration());
    Set<CtTypeReference<?>> allConcreteClasses = CtModelExt.INSTANCE.getConcreteClassesFromClass(declarationType.getTypeDeclaration());
    typeReferences.addAll(allInterfaces);
    typeReferences.addAll(allConcreteClasses);
    List<List<CtTypeReference<?>>> typeArgumentsCombinations =
            generateAllPossibleTypeArgumentsCombination(wildcardArguments.size(), typeReferences);
    List<CtTypeReference<?>> candidates = new ArrayList<>();
    for (List<CtTypeReference<?>> typeArguments : typeArgumentsCombinations) {
      CtTypeReference<?> clone = declarationType.clone();
      clone.setActualTypeArguments(typeArguments);
      candidates.add(clone);
    }
    return candidates;
  }

  public static List<CtTypeReference<?>> fillWildcardsWithRandomTypes(CtTypeReference<?> declarationType) {
    List<CtWildcardReference> wildcardArguments = TypeUtils.getWildcardArguments(declarationType);
    if (wildcardArguments.isEmpty()) {
      return new ArrayList<>(Arrays.asList(declarationType.clone()));
    }

    List<CtTypeReference<?>> typeReferences = new ArrayList<>(TypeUtils.defaultTypeParameters());
    Set<CtTypeReference<?>> allInterfaces = CtModelExt.INSTANCE.getInterfacesFromClass(TestGenContext.getMainType());
    Set<CtTypeReference<?>> allConcreteClasses = CtModelExt.INSTANCE.getConcreteClassesFromClass(TestGenContext.getMainType());
    typeReferences.addAll(allInterfaces);
    typeReferences.addAll(allConcreteClasses);

    List<List<CtTypeReference<?>>> allCombinations = generateAllPossibleTypeArgumentsCombination(wildcardArguments.size(), typeReferences);

    List<CtTypeReference<?>> results = new ArrayList<>();
    for (List<CtTypeReference<?>> combination : allCombinations) {
      CtTypeReference<?> result = declarationType.clone();
      int i = 0;
      for (CtWildcardReference wildcardArgument : TypeUtils.getWildcardArguments(result)) {
        wildcardArgument.replace(combination.get(i++));
      }
      results.add(result);
    }
    return results;
  }

  public static CtTypeReference<?> fillSelfReferringSubtypes(CtTypeReference<?> genericType) {
    String qualifiedName = genericType.getQualifiedName();
    List<CtTypeReference<?>> ctTypeReferences =
            resolvedGenericTypeExamples.get(qualifiedName);
    if (ctTypeReferences == null) {
      ctTypeReferences = searchActualTypeArgumentExamples(TestGenContext.getCtPackage(), qualifiedName);
      if (ctTypeReferences.isEmpty()) {
        ctTypeReferences = searchActualTypeArgumentExamples(TestGenContext.getCtPackage().getParent(), qualifiedName);
      }
    }
    resolvedGenericTypeExamples.put(qualifiedName, ctTypeReferences);
    return RandomUtils.select(ctTypeReferences);
  }

  private static List<CtTypeReference<?>> searchActualTypeArgumentExamples(CtQueryable queryRoot,
                                                                           String qualifiedName) {
    return queryRoot.filterChildren(new TypeFilter<>(CtTypeReference.class))
            .select((CtTypeReference<?> t) -> t.getQualifiedName().equals(qualifiedName))
            .select((CtTypeReference<?> t) -> t.filterChildren(new TypeFilter<>(CtTypeParameterReference.class))
                    .list().isEmpty())
            .select((CtTypeReference<?> t) -> !TypeUtils.isRawType(t))
            .list();
  }

  public static List<List<CtTypeReference<?>>> generateAllPossibleTypeArgumentsCombination(
          int targetCount, List<CtTypeReference<?>> candidates) {
    int candidateSize = candidates.size();
    int wholeSize = (int) Math.pow(candidateSize, targetCount);
    List<List<CtTypeReference<?>>> substitutions = new ArrayList<>();
    for (int i = 0; i < wholeSize; i++) {
      substitutions.add(new ArrayList<>());
    }

    int repeat = wholeSize / candidateSize;
    for (int pos = 0; pos < targetCount; pos++) {
      for (int i = 0; i < wholeSize; i++) {
        List<CtTypeReference<?>> list = substitutions.get(i);
        CtTypeReference<?> ctTypeReference = candidates.get((i / repeat) % candidateSize).clone();
        list.add(ctTypeReference);
      }
      repeat /= candidateSize;
    }
    return substitutions;
  }
}
