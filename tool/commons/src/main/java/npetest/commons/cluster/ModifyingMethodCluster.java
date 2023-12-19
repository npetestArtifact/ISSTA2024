package npetest.commons.cluster;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;
import java.util.stream.Collectors;

public class ModifyingMethodCluster {
  private static final ModifyingMethodCluster instance = new ModifyingMethodCluster();

  private ModifyingMethodCluster() {
  }

  public static ModifyingMethodCluster getInstance() {
    return instance;
  }

  private final Map<String, List<CtMethod<?>>> voidMethodCache = new HashMap<>();

  private final Map<String, Set<CtMethod<?>>> methodCache = new HashMap<>();

  private final Map<String, List<CtMethod<?>>> selfReturningMethodCache = new HashMap<>();

  public Set<CtMethod<?>> getMethods(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getQualifiedName();
    if (methodCache.containsKey(qualifiedName)) {
      return methodCache.get(qualifiedName);
    }
    CtType<?> ctType = typeReference.getTypeDeclaration();
    Set<CtMethod<?>> methods = getMethodsFromCtType(ctType);
//    if (methods.isEmpty()) {
      CtTypeReference<?> superclass = ctType.getSuperclass();
      if (superclass != null) {
        methods.addAll(getMethodsFromCtType(superclass.getTypeDeclaration()));
      }
      Set<CtTypeReference<?>> superInterfaces = ctType.getSuperInterfaces();
      for (CtTypeReference<?> superInterface : superInterfaces) {
        methods.addAll(getMethodsFromCtType(superInterface.getTypeDeclaration()));
      }
//    }
    methodCache.put(qualifiedName, methods);
    return methods;
  }

  private Set<CtMethod<?>> getMethodsFromCtType(CtType<?> ctType) {
    return ctType.getMethods().stream()
            .filter(m -> !m.isStatic())
            .filter(m -> !m.isDefaultMethod())
            .filter(m -> !m.isPrivate())
            .collect(Collectors.toSet());
  }

  public List<CtMethod<?>> getVoidMethods(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getQualifiedName();
    if (voidMethodCache.containsKey(qualifiedName)) {
      return voidMethodCache.get(qualifiedName);
    }
    CtType<?> ctType = typeReference.getTypeDeclaration();
    List<CtMethod<?>> voidMethods = getVoidMethodsFromCtType(ctType);
    if (voidMethods.isEmpty()) {
      CtTypeReference<?> superclass = ctType.getSuperclass();
      if (superclass != null) {
        voidMethods.addAll(getVoidMethodsFromCtType(superclass.getTypeDeclaration()));
      }
      Set<CtTypeReference<?>> superInterfaces = ctType.getSuperInterfaces();
      for (CtTypeReference<?> superInterface : superInterfaces) {
        voidMethods.addAll(getVoidMethodsFromCtType(superInterface.getTypeDeclaration()));
      }
    }
    voidMethodCache.put(qualifiedName, voidMethods);
    return voidMethods;
  }

  private List<CtMethod<?>> getVoidMethodsFromCtType(CtType<?> ctType) {
    return ctType.getMethods().stream()
            .filter(m -> !m.isStatic())
            .filter(m -> !m.isDefaultMethod())
            .filter(m -> !m.isPrivate())
            .filter(m -> m.getType().equals(TypeUtils.voidPrimitive()))
            .collect(Collectors.toList());
  }

  public List<CtMethod<?>> getSelfReturningMethods(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getQualifiedName();
    if (selfReturningMethodCache.containsKey(qualifiedName)) {
      return selfReturningMethodCache.get(qualifiedName);
    }
    CtType<?> ctType = typeReference.getTypeDeclaration();
    List<CtMethod<?>> voidMethods = ctType.getMethods().stream()
            .filter(m -> !m.isStatic())
            .filter(m -> !m.isPrivate())
            .filter(m -> m.getType().getQualifiedName().equals(m.getDeclaringType().getQualifiedName()))
            .collect(Collectors.toList());
    selfReturningMethodCache.put(qualifiedName, voidMethods);
    return voidMethods;
  }

  public int countModifyingMethods(CtTypeReference<?> typeReference) {
    return getVoidMethods(typeReference).size()
            + getSelfReturningMethods(typeReference).size();
  }
}
