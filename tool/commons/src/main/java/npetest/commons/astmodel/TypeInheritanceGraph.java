package npetest.commons.astmodel;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import npetest.commons.filters.NonTestCodeFilter;
import npetest.commons.misc.Debugger;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeAccessibilityChecker;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.util.EmptyClearableList;

public class TypeInheritanceGraph {
  private static final Logger logger = LoggerFactory.getLogger(TypeInheritanceGraph.class);

  private final EdgeReversedGraph<String, InheritanceEdge> reversedGraph;

  private final Map<String, List<String>> concreteSubTypeCache = new HashMap<>();

  private final Map<String, List<CtTypeReference<?>>> subInstanceTypeCache = new HashMap<>();

  private TypeInheritanceGraph(TypeInheritanceGraphBuilder builder) {
    this.reversedGraph = builder.reversedGraph;
  }

  public List<String> getDirectSubTypes(String typeKey) {
    if (!reversedGraph.containsVertex(typeKey)) {
      return new ArrayList<>();
    }
    List<String> subTypes = new ArrayList<>();
    Set<InheritanceEdge> inheritanceEdges = reversedGraph.outgoingEdgesOf(typeKey);
    for (InheritanceEdge inheritanceEdge : inheritanceEdges) {
      subTypes.add(reversedGraph.getEdgeTarget(inheritanceEdge));
    }

    return subTypes;
  }

  public List<String> getSubTypes(String typeKey) {
    if (!reversedGraph.containsVertex(typeKey)) {
      return new ArrayList<>();
    }

    List<String> subTypes = concreteSubTypeCache.get(typeKey);
    if (subTypes == null) {
      Iterator<String> iterator = new DepthFirstIterator<>(reversedGraph, typeKey);
      subTypes = new ArrayList<>();
      while (iterator.hasNext()) {
        String subTypeKey = iterator.next();
        subTypes.add(subTypeKey);
      }
      concreteSubTypeCache.put(typeKey, subTypes);
    }
    return subTypes;
  }

  public List<CtTypeReference<?>> findCandidateConcreteSubtypes(CtTypeReference<?> superType) {

    String superTypeKey = superType.getQualifiedName();
    if (subInstanceTypeCache.containsKey(superType.toString())) {
      // use cached result
      return subInstanceTypeCache.get(superType.toString());
    }

    if (!reversedGraph.containsVertex(superTypeKey)) {
      return Collections.emptyList();
    }

    Iterator<String> iterator = new DepthFirstIterator<>(reversedGraph, superTypeKey);
    List<CtTypeReference<?>> concreteAdaptedTypeCandidates = new ArrayList<>();
    if (!iterator.hasNext()) {
      return concreteAdaptedTypeCandidates;
    }
    // skip starting point, the type itself.
    String previousType = iterator.next();
    String currentType;
    Deque<CtTypeReference<?>> branchingPoints = new ArrayDeque<>();
    if (reversedGraph.outDegreeOf(superTypeKey) > 1) {
      branchingPoints.push(superType);
    }
    CtTypeReference<?> currentTypingContext = superType;
    boolean continueFlag = false;
    while (iterator.hasNext()) {
      currentType = iterator.next();
      if (continueFlag) {
        if (!reversedGraph.containsEdge(previousType, currentType)) {
          currentTypingContext = updateTypingContextFromStack(branchingPoints, currentType);
          continueFlag = false;
        } else {
          previousType = currentType;
          continue;
        }
      }

      if (!reversedGraph.containsEdge(previousType, currentType)) {
        currentTypingContext = updateTypingContextFromStack(branchingPoints, currentType);
      }

      CtType<?> subType = CtModelExt.INSTANCE.queryCtType(currentType);
      if (subType == null) {
        // failed in this DFS path. keep continuing until initial context reach
        continueFlag = true;
        previousType = currentType;
        continue;
      }

      /* CtTypeReference<?> cannot be created properly for anonymous type,
       * so it is not supported. But it may be possible to create instance
       * of anonymous type by factory method and pass it to method input
       */
      if (subType.isAnonymous()) {
        continueFlag = true;
        previousType = currentType;
        boolean failure = false;
        if (!subType.isShadow()) {
          CtTypeReference<?> superclass = subType.getSuperclass();
          if (superclass == null) {
            Set<CtTypeReference<?>> superInterfaces = subType.getSuperInterfaces();
            for (CtTypeReference<?> superInterface : superInterfaces) {
              if (superInterface.isGenerics()) {
                failure = true;
                break;
              }
            }
          } else {
            if (superclass.isGenerics()) {
              failure = true;
            }
          }
        } else {
          failure = true;
        }
        if (!failure && TypeAccessibilityChecker.isGloballyAssignableAnonymousClass(subType)) {
          concreteAdaptedTypeCandidates.add(subType.getReference());
        }
        continue;
      }

      // 1. Fill actual type arguments with type variables as placeholders.
      CtTypeReference<?> superTypeRefInAST = ASTUtils.findMatchingSuperTypeReference(subType, currentTypingContext);
      if (superTypeRefInAST == null) {
        /* This might not happen because the previous checking of
         * `reversedGraph.containsEdge(lastBranchingPoint.getQualifiedName(), currentType)`
         * is supposed to prevent this case.
         */
        String message = "Unexpected result of ASTUtils.findMatchingSuperTypeReference(a, b)\n"
                + "a(subType): " + subType.getQualifiedName() + '\n'
                + "b(currentTypingContext): " + currentTypingContext + '\n';
        Debugger.logUnexpectedException(message);
        continueFlag = true;
        previousType = currentType;
        continue;
      }
      CtTypeReference<?> subTypeReference = subType.getReference();
      List<CtTypeParameter> formalCtTypeParameters = subType.getFormalCtTypeParameters();
      subTypeReference.setActualTypeArguments(formalCtTypeParameters.stream()
              .map(CtTypeParameter::getReference).collect(Collectors.toList()));

      // 2. Adapt subTypeReference
      int i = 0;
      boolean failure = false;
      for (CtTypeReference<?> actualTypeArgument : currentTypingContext.getActualTypeArguments()) {
        /* Currently not supporting class that extending generic class without type parameters */
        List<CtTypeReference<?>> actualTypeArguments = superTypeRefInAST.getActualTypeArguments();
        if (actualTypeArguments instanceof EmptyClearableList) {
          failure = true;
          break;
        }

        CtTypeReference<?> typeArgumentInAST = actualTypeArguments.get(i);
        if (typeArgumentInAST instanceof CtTypeParameterReference) {
          /* Additional checking if the type parameter is bounded in the target subtype declaration */
          if (typeArgumentInAST.getTypeDeclaration() == null ||
                  !typeArgumentInAST.getTypeDeclaration().getParent().equals(subType)) {
            failure = true;
            break;
          }
          subTypeReference.getActualTypeArguments().set(
                  subTypeReference.getTypeDeclaration().getFormalCtTypeParameters().stream()
                          .map(CtFormalTypeDeclarer::getSimpleName).collect(Collectors.toList())
                          .indexOf(typeArgumentInAST.getSimpleName()),
                  actualTypeArgument);
          if (subTypeReference.getElements(new TypeFilter<>(CtTypeParameterReference.class)).isEmpty()) {
            break;
          }
        } else {
          if (!typeArgumentInAST.toString().equals(actualTypeArgument.toString())) {
            failure = true;
            break;
          }
        }
        i++;
      }
      if (failure) {
        continueFlag = true;
        previousType = currentType;
        continue;
      }

      if (!subType.isAbstract() && !subType.isInterface()) {
        concreteAdaptedTypeCandidates.add(subTypeReference);
      }
      currentTypingContext = subTypeReference;
      if (reversedGraph.outDegreeOf(currentType) > 1 && (branchingPoints.isEmpty() ||
              !branchingPoints.peek().equals(currentTypingContext))) {
        branchingPoints.push(currentTypingContext);
      }

      previousType = currentType;
    }

    List<CtTypeReference<?>> usableAdaptedSubTypes = concreteAdaptedTypeCandidates.stream()
            .filter(t -> TypeAccessibilityChecker.isGloballyAccessible(t.getTypeDeclaration()))
            .collect(Collectors.toList());
    subInstanceTypeCache.put(superTypeKey, usableAdaptedSubTypes);
    return usableAdaptedSubTypes;
  }

  private CtTypeReference<?> updateTypingContextFromStack(Deque<CtTypeReference<?>> branchingPoints, String currentType) {
    CtTypeReference<?> typingContext = branchingPoints.pop();
    while (!reversedGraph.containsEdge(typingContext.getQualifiedName(), currentType)) {
      if (branchingPoints.isEmpty()) {
        break;
      }
      typingContext = branchingPoints.pop();
    }
    branchingPoints.push(typingContext);
    return typingContext;
  }

  public static class TypeInheritanceGraphBuilder {
    private final CtModel ctModel;

    private final Graph<String, InheritanceEdge> graph = new DefaultDirectedGraph<>(InheritanceEdge.class);

    private EdgeReversedGraph<String, InheritanceEdge> reversedGraph;

    public TypeInheritanceGraphBuilder(CtModel ctModel) {
      this.ctModel = ctModel;
    }

    public TypeInheritanceGraph build() {
      updateGraphFromSpoonCtType();
      updateGraphFromUnloadedType();
      this.reversedGraph = new EdgeReversedGraph<>(graph);
      return new TypeInheritanceGraph(this);
    }

    /**
     * Update the graph from the types in the Spoon model.
     */
    private void updateGraphFromSpoonCtType() {
      List<CtType<?>> allTypes = ctModel
              .filterChildren((CtType<?> type) -> !(type instanceof CtTypeParameter)
                      && !type.isLocalType() && !type.isAnnotationType())
              .select(NonTestCodeFilter.INSTANCE).list();

      for (CtType<?> type : allTypes) {
        updateGraphFromCtType(type);
      }
    }

    /**
     * Update the graph from the types in the auxiliary classpath,
     * which is shadow in the Spoon model.
     */
    private void updateGraphFromUnloadedType() {
      for (String cpEntry : CtModelExt.INSTANCE.getAuxiliaryClasspath()) {
        try (JarFile jarFile = new JarFile(cpEntry)) {
          Enumeration<JarEntry> entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            if (jarEntry.isDirectory() || !ClassNameUtils.isNamedClass(jarEntry.getName())) {
              continue;
            }
            InputStream inputStream = jarFile.getInputStream(jarEntry);
            ClassReader classReader = new ClassReader(inputStream);
            updateGraphFromClassReader(classReader);
          }
        } catch (IOException e) {
          logger.error("Error when reading jar file: {}", cpEntry, e);
        }
      }
    }

    private void updateGraphFromCtType(CtType<?> type) {
      CtTypeReference<?> superclass = type.getSuperclass();
      String typeKey = type.getQualifiedName();
      if (superclass != null) {
        addEdge(typeKey, superclass.getQualifiedName(), InheritanceEdge.ofExtends());
      }

      Set<CtTypeReference<?>> superInterfaces = type.getSuperInterfaces();
      for (CtTypeReference<?> superInterface : superInterfaces) {
        addEdge(typeKey, superInterface.getQualifiedName(), InheritanceEdge.ofImplements());
      }
    }

    private void addEdge(String subtypeKey, String superTypeKey, InheritanceEdge e) {
      graph.addVertex(subtypeKey);
      graph.addVertex(superTypeKey);
      graph.addEdge(subtypeKey, superTypeKey, e);
    }

    private void updateGraphFromClassReader(ClassReader classReader) {
      String typeKey = classReader.getClassName().replace('/', '.');
      ClassNode classNode = new ClassNode();
      classReader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

      if (classNode.superName != null) {
        String superclassName = classNode.superName.replace('/', '.');
        addEdge(typeKey, superclassName, InheritanceEdge.ofExtends());
      }

      List<String> superInterfaceNames = classNode.interfaces.stream().map(s -> s.replace('/', '.'))
              .collect(Collectors.toList());
      for (String superInterfaceName : superInterfaceNames) {
        addEdge(typeKey, superInterfaceName, InheritanceEdge.ofImplements());
      }
    }
  }

  static class InheritanceEdge extends DefaultEdge {
    static final String EXTENDS = "extends";
    static final String IMPLEMENTS = "implements";

    String inheritanceType;

    public static InheritanceEdge ofExtends() {
      return new InheritanceEdge(EXTENDS);
    }

    public static InheritanceEdge ofImplements() {
      return new InheritanceEdge(IMPLEMENTS);
    }

    @Override
    public String getSource() {
      return (String) super.getSource();
    }

    @Override
    public String getTarget() {
      return (String) super.getTarget();
    }

    private InheritanceEdge(String inheritanceType) {
      switch (inheritanceType) {
        case EXTENDS:
        case IMPLEMENTS:
          this.inheritanceType = inheritanceType;
          break;
        default:
          throw new IllegalStateException();
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(getSource(), getTarget());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (getClass() != o.getClass()) {
        return false;
      }
      InheritanceEdge edge = (InheritanceEdge) o;
      return Objects.equals(getSource(), edge.getSource()) && Objects.equals(getTarget(), edge.getTarget())
              && inheritanceType.equals(edge.inheritanceType);
    }

    @Override
    public String toString() {
      return super.toString() + " := " + inheritanceType;
    }
  }
}
