package npetest.commons.astmodel;

import npetest.commons.astmodel.TypeInheritanceGraph.TypeInheritanceGraphBuilder;
import npetest.commons.filters.*;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.TypeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.PrettyPrinter;
import spoon.reflect.visitor.chain.CtQueryable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.stream.Collectors;

public class CtModelExt {
  private static final Logger logger = LoggerFactory.getLogger(CtModelExt.class);

  private final CtModel ctModel;

  private TypeInheritanceGraph typeInheritanceGraph;

  private final ClassLoader mainLoader;

  private final ClassLoader auxiliaryLoader;

  private final Factory factory;

  private final PrettyPrinter printer;


  private final String targetClasspath;

  private final List<String> auxiliaryClasspath;

  public static CtModelExt INSTANCE;

  private final Set<TypeKey> cuts = new HashSet<>();

  private final Set<ExecutableKey> muts = new HashSet<>();

  private final Set<ExecutableKey> prunedMUTs = new HashSet<>();

  private final Map<String, CtMethod<?>> methodQueryCache = new HashMap<>();

  private final Map<String, CtType<?>> shadowTypeCache = new HashMap<>();

  private final Set<String> unresolvedShadowTypes = new HashSet<>();

  private List<CtType<?>> enumReferencePool;

  private final HashMap<String, List<CtMethod<?>>> generatorsCache = new HashMap<>();

  private final HashMap<String, Set<CtTypeReference<?>>> interfaceCache = new HashMap<>();

  private final HashMap<String, Set<CtTypeReference<?>>> concreteClassCache = new HashMap<>();

  // ------------------------------------------------------------------------------------------
  private final Set<ExecutableKey> allMethods = new HashSet<>();

  private final Set<CtField<?>> allFields = new HashSet<>();

  private CtModelExt(CtModelExtBuilder builder) {
    this.factory = builder.factory;
    this.mainLoader = builder.loader;
    this.auxiliaryLoader = builder.loader.getParent();
    this.printer = builder.printer;
    this.ctModel = builder.launcher.buildModel();
    this.targetClasspath = builder.targetClasspath;
    this.auxiliaryClasspath = builder.auxiliaryClasspath;
  }

  public void addMUT(ExecutableKey methodKey) {
    muts.add(methodKey);
  }

  public PrettyPrinter getPrinter() {
    return printer;
  }

  public void createTypeInheritanceGraph() {
    this.typeInheritanceGraph = new TypeInheritanceGraphBuilder(ctModel).build();
  }

  public List<CtType<?>> findConcreteSubtypes(CtType<?> ctType) {
    List<String> subTypeKeys = typeInheritanceGraph.getSubTypes(ctType.getQualifiedName());
    List<CtType<?>> subTypes = new ArrayList<>();
    for (String subTypeKey : subTypeKeys) {
      CtType<?> subType = queryCtType(subTypeKey);
      if (subType != null && !subType.isAbstract() && !subType.isInterface()) {
        subTypes.add(subType);
      }
    }
    return subTypes;
  }

  public List<CtType<?>> findDirectSubtypes(CtType<?> ctType) {
    List<String> directSubTypes = typeInheritanceGraph.getDirectSubTypes(ctType.getQualifiedName());
    return directSubTypes.stream()
            .map(CtModelExt.INSTANCE::queryCtType)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }

  public List<CtTypeReference<?>> findCandidateConcreteSubtypes(CtTypeReference<?> typeReference) {
    return typeInheritanceGraph.findCandidateConcreteSubtypes(typeReference);
  }

  public CtCompilationUnit getCompilationUnit(CtType<?> ctType) {
    CtType<?> topLevelType = getTopLevelType(ctType);
    for (CtCompilationUnit compilationUnit : factory.CompilationUnit().getMap().values()) {
      CtType<?> mainType;
      try {
        mainType = compilationUnit.getMainType();
      } catch (Exception e) {
        mainType = null;
      }
      if (mainType == null) {
        continue;
      }
      if (TypeKey.of(mainType).equals(TypeKey.of(topLevelType))) {
        return compilationUnit;
      }
    }
    return null;
  }

  public CtType<?> getTopLevelType(CtType<?> ctType) {
    CtType<?> parent = ctType;
    while (parent != null) {
      if (parent.isTopLevel()) {
        return parent;
      } else {
        parent = parent.getParent(CtType.class);
      }
    }
    // must not happen
    return null;
  }

  public ClassLoader getMainLoader() {
    // try {
    //   mainLoader.loadClass("npetest.analysis.dynamicanalysis.MethodTrace.class");
    // } catch (ClassNotFoundException e) {
    //   // TODO Auto-generated catch block
    //   logger.debug("GET MAIN LOADER PROB");
    //   e.printStackTrace();
    // }
    return mainLoader;
  }

  public ClassLoader getAuxiliaryLoader() {
    return auxiliaryLoader;
  }

  public List<String> getAuxiliaryClasspath() {
    return auxiliaryClasspath;
  }

  public String getTargetClasspath() {
    return targetClasspath;
  }

  public CtModel getCtModel() {
    return ctModel;
  }

  public Factory getFactory() {
    return factory;
  }

  public CtType<?> getCtTypeFromModel(String qualifiedName) {
    return factory.Type().get(qualifiedName);
  }

  public CtType<?> getShadowType(Class<?> clazz) {
    CtTypeReference<?> ctTypeReference = factory.Type().createReference(clazz);
    return ctTypeReference.getTypeDeclaration();
  }

  /**
   * Scan variable declarations of given type from CtModel,
   * and take actual type references from them.
   *
   * @param type type that declarations to be scanned have.
   * @return List of actual type references in declarations of given type.
   * Each element must not be generic.
   */
  public List<CtTypeReference<?>> gatherActualTypesFromDeclarations(CtType<?> type) {
    return ctModel
            .getElements(new DeclarationFilter(type)).stream()
            .map(CtTypedElement::getType)
            // Not sure that two subsequent filters are redundant.
            .filter(t -> t.getElements(new TypeFilter<>(CtTypeParameterReference.class)).isEmpty())
            .filter(CtTypeInformation::isParameterized)
            .filter(t -> t.filterChildren(new TypeFilter<>(CtTypeReference.class))
                    .select((CtTypeReference<?> typ) -> !typ.equals(t))
                    .select((CtTypeReference<?> typ) -> typ.getTypeDeclaration() != null)
                    .map((CtTypeReference<?> typ) -> typ.getTypeDeclaration())
                    .select((CtType<?> ctType) -> !NonTestCodeFilter.INSTANCE.matches(ctType))
                    .list().isEmpty())
            .filter(t -> NonTestCodeFilter.INSTANCE.matches(t.getTypeDeclaration()))
            .distinct()
            .collect(Collectors.toList());
  }

  /**
   * Scan constructor calls of given type from CtModel,
   * and take their actual type references.
   *
   * @param type type that constructor calls to be scanned have.
   * @return List of actual type references in constructor calls to create
   * instance of given type. Each element must not be generic.
   */
  public List<CtTypeReference<?>> gatherActualTypesFromConstructorCall(CtType<?> type) {
    return ctModel
            .getElements(new ConstructorCallFilter(type)).stream()
            .map(CtConstructorCall::getType)
            // Not sure that two subsequent filters are redundant.
            .filter(t -> !t.toString().contains("<>"))
            .filter(t -> !t.isGenerics())
            .filter(t -> t.getElements(new TypeFilter<>(CtTypeParameterReference.class)).isEmpty())
            .filter(CtTypeInformation::isParameterized)
            .filter(t -> t.filterChildren(new TypeFilter<>(CtTypeReference.class))
                    .select((CtTypeReference<?> typ) -> !typ.equals(t))
                    .select((CtTypeReference<?> typ) -> typ.getTypeDeclaration() != null)
                    .map((CtTypeReference<?> typ) -> typ.getTypeDeclaration())
                    .select((CtType<?> ctType) -> !NonTestCodeFilter.INSTANCE.matches(ctType))
                    .list().isEmpty())
            .filter(t -> NonTestCodeFilter.INSTANCE.matches(t.getTypeDeclaration()))
            .distinct()
            .collect(Collectors.toList());
  }

  public List<CtTypeReference<?>> getAllEnumTypeReferences() {
    if (enumReferencePool == null) {
      List<CtEnum<?>> enumReferences = ctModel.filterChildren(new TypeFilter<>(CtEnum.class))
              .select(NonTestCodeFilter.INSTANCE)
              .list();

      if (enumReferences.isEmpty()) {
        enumReferences.addAll(ctModel.filterChildren(new TypeFilter<>(CtTypeReference.class))
                .select((CtTypeReference<?> typeReference) ->
                        typeReference.getTypeDeclaration() != null && typeReference.getTypeDeclaration().isEnum())
                .map((CtTypeReference<?> typeReference) -> (CtEnum<?>) typeReference.getTypeDeclaration())
                .select(NonTestCodeFilter.INSTANCE)
                .list());
      }

      enumReferencePool = enumReferences.stream().distinct().collect(Collectors.toList());
    }
    return enumReferencePool.stream().map(CtType::getReference).collect(Collectors.toList());
  }

  public List<CtLiteral<?>> queryLiterals(CtQueryable queryRoot, CtTypeReference<?> primitiveType) {
    if (queryRoot == null) {
      return new ArrayList<>();
    }
    return queryRoot.filterChildren(LiteralFilter.INSTANCE.setType(primitiveType)).list();
  }

  public Map<String, CtMethod<?>> getMethodQuery() {
    return methodQueryCache;
  }

  public CtMethod<?> getMethodFromKey(String methodKey) {
    if (methodQueryCache.containsKey(methodKey)) {
      return methodQueryCache.get(methodKey);
    }

    String typeName = methodKey.split("#")[0];
    String methodSignature = methodKey.split("#")[1];
    CtType<?> type = CtModelExt.INSTANCE.queryCtType(typeName);
    if (type == null) {
      return null;
    }

    CtMethod<?> ctMethod = getMethodBySignature(type, methodSignature);
    methodQueryCache.put(methodKey, ctMethod);
    return ctMethod;
  }

  public CtMethod<?> getMethodBySignature(CtType<?> type, String signature) {
    List<CtMethod<?>> methods = type.getMethods().stream()
            .filter(m -> m.getSignature().equals(signature)).collect(Collectors.toList());
    if (methods.isEmpty()) {
      logger.debug("{} is not found in {}.", signature, type.getQualifiedName());
      return null;
    }

    if (methods.size() > 1) {
      logger.error("Multiple methods returned in querying {} from {}", signature, type.getQualifiedName());
      return null;
    }

    return methods.get(0);
  }

  public void addCUT(CtType<?> cut) {
    cuts.add(TypeKey.of(cut));
  }

  public Set<TypeKey> getCUTs() {
    return cuts;
  }

  public Set<ExecutableKey> getMUTs() {
    return muts;
  }

  public Set<ExecutableKey> getPrunedMUTs() {
    return prunedMUTs;
  }

  public void removeMUTs(ExecutableKey methodKey) {
    muts.remove(methodKey);
  }

  public void updatePrunedMUTs(ExecutableKey methodKey) {
    prunedMUTs.add(methodKey);
  }

  public List<CtMethod<?>> searchGenerators(CtTypeReference<?> typeReference) {
    List<CtMethod<?>> generators = generatorsCache.computeIfAbsent(
            typeReference.getQualifiedName(), name -> new ArrayList<>());
    generators.addAll(ctModel.filterChildren(GeneratorMethodFilter.INSTANCE.setReturnType(typeReference)).list());
    return generators;
  }

  public CtType<?> queryCtType(String qualifiedName) {
    CtType<?> ctType = factory.Type().get(qualifiedName);
    if (ctType == null && !unresolvedShadowTypes.contains(qualifiedName)) {
      ctType = shadowTypeCache.get(qualifiedName);
      if (ctType != null) {
        return ctType;
      }

      ctType = ShadowTypeBuilder.createShadowCtType(qualifiedName);
      if (ctType == null) {
        unresolvedShadowTypes.add(qualifiedName);
      } else {
        shadowTypeCache.put(qualifiedName, ctType);
      }
    }
    return ctType;
  }

  public Set<CtTypeReference<?>> getInterfacesFromClass(CtType<?> ctType) {
    String qualifiedName = ctType.getQualifiedName();
    Set<CtTypeReference<?>> interfaces = interfaceCache.get(qualifiedName);
    if (interfaces != null) {
      return interfaces;
    }
    interfaces = new HashSet<>(ctType.filterChildren(new TypeFilter<>(CtTypeReference.class))
            .select((CtTypeReference<?> ref) -> ref.getTypeDeclaration() != null &&
                    ref.getTypeDeclaration().isInterface())
            .map((CtTypeReference<?> ref) -> ref.getTypeDeclaration().getReference())
            .list());
    Set<CtTypeReference<?>> filteredInterfaces = interfaces.stream()
            .filter(t -> !t.getTypeDeclaration().isGenerics()).collect(Collectors.toSet());
    interfaceCache.put(qualifiedName, filteredInterfaces);
    return filteredInterfaces;
  }

  public Set<CtTypeReference<?>> getInterfacesFromPackage(CtPackage ctPackage) {
    String packageName = ctPackage.getQualifiedName();
    Set<CtTypeReference<?>> interfaces = interfaceCache.get(packageName);
    if (interfaces != null) {
      return interfaces;
    }
    interfaces = new HashSet<>(ctPackage.filterChildren(new TypeFilter<>(CtInterface.class))
            .map((CtInterface<?> i) -> i.getReference()).list());
    Set<CtTypeReference<?>> filteredInterfaces = interfaces.stream()
            .filter(t -> !t.getTypeDeclaration().isGenerics()).collect(Collectors.toSet());
    interfaceCache.put(packageName, filteredInterfaces);
    return filteredInterfaces;
  }

  public Set<CtTypeReference<?>> getConcreteClassesFromClass(CtType<?> ctType) {
    String qualifiedName = ctType.getQualifiedName();
    Set<CtTypeReference<?>> concreteClasses = concreteClassCache.get(qualifiedName);
    if (concreteClasses != null) {
      return concreteClasses;
    }
    concreteClasses = new HashSet<>(ctType.filterChildren(new TypeFilter<>(CtTypeReference.class))
            .select((CtTypeReference<?> ref) -> ref.getTypeDeclaration() != null &&
                    !ref.getTypeDeclaration().isAbstract())
            .map((CtTypeReference<?> ref) -> ref.getTypeDeclaration().getReference())
            .list());
    Set<CtTypeReference<?>> filteredConcreteClasses = concreteClasses.stream()
            .filter(t -> !t.getTypeDeclaration().isGenerics()).collect(Collectors.toSet());
    concreteClassCache.put(qualifiedName, filteredConcreteClasses);
    return filteredConcreteClasses;
  }

  public Set<CtTypeReference<?>> getConcreteClassesFromPackage(CtPackage ctPackage) {
    String packageName = ctPackage.getQualifiedName();
    Set<CtTypeReference<?>> concreteClasses = concreteClassCache.get(packageName);
    if (concreteClasses != null) {
      return concreteClasses;
    }
    concreteClasses = new HashSet<>(ctPackage.filterChildren(new TypeFilter<>(CtClass.class))
            .select((CtClass<?> c) -> !c.isAbstract())
            .map((CtClass<?> c) -> c.getReference())
            .list());
    Set<CtTypeReference<?>> filteredConcreteClasses = concreteClasses.stream()
            .filter(t -> !t.getTypeDeclaration().isGenerics()).collect(Collectors.toSet());
    concreteClassCache.put(packageName, filteredConcreteClasses);
    return filteredConcreteClasses;
  }

  public List<CtConstructor<?>> getConstructors(CtTypeReference<?> typeReference) {
    Set<? extends CtExecutable<?>> constructors = ((CtClass<?>) typeReference.getTypeDeclaration()).getConstructors();
    List<CtConstructor<?>> results = new ArrayList<>();
    for (CtExecutable<?> constructor : constructors) {
      if (((CtModifiable) constructor).isPrivate()) {
        continue;
      }
      results.add((CtConstructor<?>) constructor);
    }
    return results;
  }

  public static class CtModelExtBuilder {
    String[] maskDependencyJars = new String[]{
            "junit", "hamcrest-core"};

    Factory factory;

    Launcher launcher;

    PrettyPrinter printer;

    ClassLoader loader;

    String targetClasspath;

    List<String> auxiliaryClasspath;

    public CtModelExtBuilder setFactory(Factory factory) {
      this.factory = factory;
      return this;
    }

    public CtModelExtBuilder setLauncher(Launcher launcher) {
      this.launcher = launcher;
      return this;
    }

    public CtModelExtBuilder setPrinter(PrettyPrinter printer) {
      this.printer = printer;
      return this;
    }

    public CtModelExtBuilder setLoader(ClassLoader loader) {
      this.loader = loader;
      return this;
    }

    public CtModelExtBuilder setTargetClasspath(String targetClasspath) {
      this.targetClasspath = targetClasspath;
      return this;
    }

    public CtModelExtBuilder setAuxiliaryClasspath(String[] auxiliaryClasspath) {
      this.auxiliaryClasspath = Arrays.stream(auxiliaryClasspath)
              .filter(cp -> Arrays.stream(maskDependencyJars).noneMatch(cp::contains))
              .collect(Collectors.toList());
      return this;
    }

    public synchronized CtModelExt build() {
      INSTANCE = new CtModelExt(this);
      return INSTANCE;
    }
  }
}
