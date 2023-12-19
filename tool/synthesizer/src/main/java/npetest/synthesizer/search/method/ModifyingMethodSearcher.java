package npetest.synthesizer.search.method;

import npetest.commons.cluster.ModifyingMethodCluster;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.TestGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeInformation;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ModifyingMethodSearcher {
  private static final Logger logger = LoggerFactory.getLogger(ModifyingMethodSearcher.class);

  public abstract CtMethod<?> select(CtTypeReference<?> instanceType);

  public List<CtMethod<?>> getAccessibleMethods(CtTypeReference<?> instanceType) {
    ModifyingMethodCluster cluster = ModifyingMethodCluster.getInstance();
    List<CtMethod<?>> accessibleMethods = cluster.getMethods(instanceType).stream()
            .filter((CtMethod<?> m) -> m.isPublic()
                    || (m.getDeclaringType() != null && m.getDeclaringType().isInterface())
                    || (m.getDeclaringType() != null && m.getDeclaringType().getPackage() != null &&
                    m.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage())))
            .collect(Collectors.toList());
    logger.debug("  Candidate methods: {}", accessibleMethods.size());
    return accessibleMethods;
  }

  public List<CtMethod<?>> getAccessibleVoidMethods(CtTypeReference<?> instanceType) {
    ModifyingMethodCluster cluster = ModifyingMethodCluster.getInstance();
    List<CtMethod<?>> accessibleMethods = cluster.getVoidMethods(instanceType).stream()
            .filter((CtMethod<?> m) -> m.isPublic() || (m.getDeclaringType().getPackage() != null &&
                    m.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage())))
            .collect(Collectors.toList());
    logger.debug("  Candidate void methods: {}", accessibleMethods.size());
    return accessibleMethods;
  }

  public List<CtMethod<?>> getAccessibleSelfReturningMethods(CtTypeReference<?> instanceType) {
    ModifyingMethodCluster cluster = ModifyingMethodCluster.getInstance();
    List<CtMethod<?>> accessibleMethods = cluster.getSelfReturningMethods(instanceType).stream()
            .filter((CtMethod<?> m) -> m.isPublic() || m.getDeclaringType().getPackage() != null &&
                    m.getDeclaringType().getPackage().equals(TestGenContext.getCtPackage()))
            .collect(Collectors.toList());
    logger.debug("  Candidate factory methods: {}", accessibleMethods.size());
    return accessibleMethods;
  }

  public CtMethod<?> getTypeRelatedMethod(CtTypeReference<?> typeReference, List<CtTypeReference<?>> relatedTypes) {
    List<CtMethod<?>> modifyingMethods = getAccessibleVoidMethods(typeReference);
//    List<CtMethod<?>> accessibleVoidMethods = getAccessibleVoidMethods(typeReference);
//    List<CtMethod<?>> accessibleSelfReturningMethods = getAccessibleSelfReturningMethods(typeReference);
//    List<CtMethod<?>> modifyingMethods = new ArrayList<>();
//    modifyingMethods.addAll(accessibleVoidMethods);
//    modifyingMethods.addAll(accessibleSelfReturningMethods);
    List<CtMethod<?>> candidateMethods = new ArrayList<>();
    modifyingMethods.stream().filter(m -> m.getParameters().stream()
                    .anyMatch(p -> relatedTypes.stream().map(CtTypeInformation::getQualifiedName).collect(Collectors.toList())
                            .contains(p.getType().getQualifiedName())))
            .forEach(candidateMethods::add);
    return !candidateMethods.isEmpty() ? RandomUtils.select(candidateMethods) : RandomUtils.select(modifyingMethods);
  }

  public abstract void updateScore(TestCase testCase, TestCase result, ExecutableKey method);
}
