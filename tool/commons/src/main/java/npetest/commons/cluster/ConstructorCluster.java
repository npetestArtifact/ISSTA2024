package npetest.commons.cluster;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.spoon.ASTUtils;
import spoon.reflect.CtModelImpl;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.chain.CtQueryable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.stream.Collectors;

public class ConstructorCluster {
  private static final ConstructorCluster instance = new ConstructorCluster();

  private ConstructorCluster() {
  }

  public static ConstructorCluster getInstance() {
    return instance;
  }

  private final Map<String, Set<CtConstructor<?>>> constructorCache = new HashMap<>();

  private final Map<String, CtMethod<?>> anonymousTypeFactoryMethodCache = new HashMap<>();

  private final Map<String, Set<CtMethod<?>>> factoryMethodCache = new HashMap<>();

  private final Map<String, Set<CtField<?>>> constantObjectCache = new HashMap<>();

  private static final int CONSTANT_OBJECT_SEARCH_LIMIT = 10;

  public Set<CtConstructor<?>> getConstructors(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getQualifiedName();
    if (constructorCache.containsKey(qualifiedName)) {
      return constructorCache.get(qualifiedName);
    }
    CtType<?> typeDeclaration = typeReference.getTypeDeclaration();
    Set<? extends CtExecutable<?>> constructorsSet = typeDeclaration.isClass()
            ? ((CtClass<?>) typeDeclaration).getConstructors() : new HashSet<>();
    Set<CtConstructor<?>> constructors = new HashSet<>();
    for (CtExecutable<?> constructor : constructorsSet) {
      if (((CtModifiable) constructor).isPrivate()) {
        continue;
      }
      constructors.add((CtConstructor<?>) constructor);
    }
    constructorCache.put(qualifiedName, constructors);
    return constructors;
  }

  public CtMethod<?> getAnonymousClassFactoryMethod(CtTypeReference<?> anonymousTypeReference) {
    String qualifiedName = anonymousTypeReference.getQualifiedName();
    if (anonymousTypeFactoryMethodCache.containsKey(qualifiedName)) {
      return anonymousTypeFactoryMethodCache.get(qualifiedName);
    }
    CtClass<?> anonymousType = (CtClass<?>) anonymousTypeReference.getTypeDeclaration();
    CtNewClass<?> newClass = anonymousType.getParent(CtNewClass.class);
    CtReturn<?> ctReturn = newClass.getParent(CtReturn.class);

    /* Two cases
     * 1. MUT resides in anonymous class : already verified
     * 2. Anonymous class is a selected subtype of abstract type : non verified
     */
    CtMethod<?> factoryMethod = ctReturn == null ? null : ctReturn.getParent(CtMethod.class);
    anonymousTypeFactoryMethodCache.put(qualifiedName, factoryMethod);
    return factoryMethod;
  }

  public Set<CtMethod<?>> getFactoryMethods(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getQualifiedName();
    if (factoryMethodCache.containsKey(qualifiedName)) {
      return factoryMethodCache.get(qualifiedName);
    }
    Set<CtMethod<?>> factoryMethods = getFactoryMethodFromConstructor(typeReference);
    factoryMethodCache.put(qualifiedName, factoryMethods);
    return factoryMethods;
  }

  private Set<CtMethod<?>> getFactoryMethodFromConstructor(CtTypeReference<?> typeReference) {
    CtClass<?> ctClass = (CtClass<?>) typeReference.getTypeDeclaration();
    Set<CtMethod<?>> factoryMethods = new HashSet<>();
    for (CtConstructor<?> constructor : ctClass.getConstructors()) {
      if (constructor.isPublic()) {
        continue;
      }
      CtQueryable root = constructor.isPrivate() ?
              constructor.getDeclaringType() : constructor.getParent(CtPackage.class);
      root.filterChildren((CtConstructorCall<?> constructorCall) ->
                      ExecutableKey.of(constructorCall.getExecutable()).equals(ExecutableKey.of(constructor)))
              .forEach((CtConstructorCall<?> constructorCall) -> {
                CtElement parent = constructorCall.getParent();
                if (parent instanceof CtField<?>
                        && ((CtField<?>) parent).isFinal() && ((CtField<?>) parent).isStatic()) {
                  factoryMethods.addAll(getStaticInstanceGetter(ctClass, (CtField<?>) parent));
                } else {
                  CtMethod<?> factoryMethod = ASTUtils.getEnclosingMethod(parent);
                  if (factoryMethod != null && factoryMethod.getType().getQualifiedName().equals(
                          ctClass.getQualifiedName())) {
                    factoryMethods.add(factoryMethod);
                  }
                }
              });
    }
    return factoryMethods.stream()
            .filter(m -> m.isStatic() ||
                    (!m.getDeclaringType().getQualifiedName().equals(typeReference.getQualifiedName())
                            && m.getParameters().stream().noneMatch(p ->
                            p.getType().getQualifiedName().equals(typeReference.getQualifiedName()))))
            .collect(Collectors.toSet());
  }

  private Set<CtMethod<?>> getStaticInstanceGetter(CtClass<?> ctClass, CtField<?> fieldInstance) {
    String singletonFieldName = fieldInstance.getSimpleName();
    return new HashSet<>(ctClass.filterChildren(new TypeFilter<>(CtFieldRead.class))
            .select((CtFieldRead<?> fr) -> fr.getVariable().getSimpleName().equals(singletonFieldName))
            .select((CtFieldRead<?> fr) -> fr.getParent() instanceof CtReturn<?>)
            .map((CtFieldRead<?> fr) -> ASTUtils.getEnclosingMethod(fr))
            .select((CtMethod<?> m) -> m != null)
            .select((CtMethod<?> m) -> m.isStatic() && !m.isPrivate())
            .list());
  }

  public Set<CtField<?>> getConstantObjects(CtTypeReference<?> ctTypeReference) {
    String qualifiedName = ctTypeReference.getQualifiedName();
    if (constantObjectCache.containsKey(qualifiedName)) {
      return constantObjectCache.get(qualifiedName);
    }
    CtElement queryRoot = ctTypeReference.getTypeDeclaration();
    Set<CtField<?>> constantObjects = new HashSet<>();
    while (!(queryRoot instanceof CtModelImpl.CtRootPackage) &&
            queryRoot != null && constantObjects.size() < CONSTANT_OBJECT_SEARCH_LIMIT) {
      Set<CtField<?>> fields = new HashSet<>(queryRoot.filterChildren(new TypeFilter<>(CtField.class))
              .select((CtField<?> f) -> f.getType().getQualifiedName().equals(qualifiedName))
              .select((CtField<?> f) -> f.isFinal() && f.isStatic() && !f.isPrivate())
              .list());
      constantObjects.addAll(fields);

      queryRoot = queryRoot.getParent();
    }
    constantObjectCache.put(qualifiedName, constantObjects);
    return constantObjects;
  }

  public int countConstructors(CtTypeReference<?> typeReference) {
    if (typeReference.isAnonymous()) {
      CtMethod<?> anonymousClassFactoryMethod = getAnonymousClassFactoryMethod(typeReference);
      return anonymousClassFactoryMethod == null ? 0 : 1;
    }

    return getConstructors(typeReference).size()
            + getFactoryMethods(typeReference).size()
            + getConstantObjects(typeReference).size();

  }
}
