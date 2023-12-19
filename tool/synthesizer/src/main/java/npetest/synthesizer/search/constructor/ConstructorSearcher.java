package npetest.synthesizer.search.constructor;

import npetest.commons.cluster.ConstructorCluster;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.TestGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConstructorSearcher {
  private static final Logger logger = LoggerFactory.getLogger(ConstructorSearcher.class);

  public ConstructorSearcher() {
  }

  public CtElement select(CtTypeReference<?> instanceType) {
    List<CtElement> apis = new ArrayList<>();
    apis.addAll(getConstructor(instanceType));
    apis.addAll(getFactoryMethod(instanceType));
    apis.addAll(getConstantObject(instanceType));
    return RandomUtils.select(apis);
  }

  public Set<CtConstructor<?>> getConstructor(CtTypeReference<?> instanceType) {
    ConstructorCluster cluster = ConstructorCluster.getInstance();
    Set<CtConstructor<?>> accessibleConstructors = cluster.getConstructors(instanceType).stream()
            .filter((CtConstructor<?> c) -> c.isPublic() || (c.getDeclaringType().getPackage() != null &&
                            c.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage())))
            .collect(Collectors.toSet());
    Set<CtConstructor<?>> candidateConstructors = accessibleConstructors.stream()
            .filter(executable -> !InvocationGenerationContext.contains(executable))
            .collect(Collectors.toSet());
    Set<CtConstructor<?>> unusedAPIs = candidateConstructors.stream()
            .filter(e -> !GenerationHistory.hasGeneratorBeenChosen(ExecutableKey.of(e)))
            .collect(Collectors.toSet());
    Set<CtConstructor<?>> finalCandidates = unusedAPIs.isEmpty() ? candidateConstructors : unusedAPIs;
    logger.debug("  Candidate constructors: {}", finalCandidates.size());
    return finalCandidates;
  }

  public Set<CtMethod<?>> getFactoryMethod(CtTypeReference<?> instanceType) {
    ConstructorCluster cluster = ConstructorCluster.getInstance();
    Set<CtMethod<?>> accessibleFactoryMethods = cluster.getFactoryMethods(instanceType).stream()
            .filter((CtMethod<?> m) -> m.isPublic() || (m.getDeclaringType().getPackage() != null &&
                    m.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage())))
            .collect(Collectors.toSet());
    Set<CtMethod<?>> candidateFactoryMethods = accessibleFactoryMethods.stream()
            .filter(executable -> !InvocationGenerationContext.contains(executable))
            .collect(Collectors.toSet());
    Set<CtMethod<?>> unusedAPIs = candidateFactoryMethods.stream()
            .filter(e -> !GenerationHistory.hasGeneratorBeenChosen(ExecutableKey.of(e)))
            .collect(Collectors.toSet());
    Set<CtMethod<?>> finalCandidates = unusedAPIs.isEmpty() ? candidateFactoryMethods : unusedAPIs;
    logger.debug("  Candidate factory methods: {}", finalCandidates.size());
    return finalCandidates;
  }

  public Set<CtField<?>> getConstantObject(CtTypeReference<?> instanceType) {
    ConstructorCluster cluster = ConstructorCluster.getInstance();
    Set<CtField<?>> accessibleConstants = cluster.getConstantObjects(instanceType).stream()
            .filter((CtField<?> f) -> f.isPublic() || (f.getDeclaringType().getPackage() != null &&
                    f.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage())))
            .collect(Collectors.toSet());
    logger.debug("  Candidate factory methods: {}", accessibleConstants.size());
    return accessibleConstants;
  }
}
