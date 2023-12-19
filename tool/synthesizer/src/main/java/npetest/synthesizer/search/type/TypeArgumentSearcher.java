package npetest.synthesizer.search.type;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.logger.LoggingUtils;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.TestGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class TypeArgumentSearcher {
  private TypeArgumentSearcher() {
  }

  private static final Logger logger = LoggerFactory.getLogger(TypeArgumentSearcher.class);

  public static List<CtTypeReference<?>> getRandomActualMethodTypeParameters(CtExecutable<?> executable) {
    if (executable instanceof CtConstructor<?>) {
      return new ArrayList<>();
    }
    CtMethod<?> genericMethod = (CtMethod<?>) executable;
    logger.debug("* Randomly concretizing type parameters of generic methods {}...", ExecutableKey.of(genericMethod));
    List<CtTypeParameter> formalTypeParameters = genericMethod.getFormalCtTypeParameters();
    List<CtTypeReference<?>> actualMethodTypeParameters = new ArrayList<>();
    for (int i = 0; i < formalTypeParameters.size(); i++) {
      CtTypeParameter ctTypeParameter = formalTypeParameters.get(0);
      CtTypeReference<?> actualTypeArgument;
      if (ctTypeParameter.getSuperclass() != null) {
        actualTypeArgument = ctTypeParameter.getSuperclass();
      } else {
        actualTypeArgument = RandomUtils.select(TypeUtils.defaultTypeParameters());
      }
      assert actualTypeArgument != null;
      actualMethodTypeParameters.add(actualTypeArgument.clone());
    }
    LoggingUtils.logResolve(logger, formalTypeParameters, actualMethodTypeParameters);
    return actualMethodTypeParameters;
  }

  public static List<CtTypeReference<?>> getRandomActualTypeArguments(CtTypeReference<?> typeReference) {
    if (TestGenContext.getSeedTestCase() == null) {
      return typeReference.getTypeDeclaration().getFormalCtTypeParameters().stream()
              .map(t -> RandomUtils.select(TypeUtils.defaultTypeParameters()))
              .collect(Collectors.toList());
    } else {
      Collection<CtTypeReference<?>> candidatesFromTest = getRandomActualTypeArgumentsFromTest(TestGenContext.getSeedTestCase());
      Collection<CtTypeReference<?>> candidates = candidatesFromTest.isEmpty() ? TypeUtils.defaultTypeParameters() : candidatesFromTest;
      return typeReference.getTypeDeclaration().getFormalCtTypeParameters().stream()
              .map(t -> RandomUtils.select(candidates))
              .collect(Collectors.toList());
    }
  }

  private static Collection<CtTypeReference<?>> getRandomActualTypeArgumentsFromTest(TestCase testCase) {
    return testCase.getCtStatements().filterChildren(new TypeFilter<>(CtTypeReference.class))
            .select((CtTypeReference<?> t) -> t.getRoleInParent().equals(CtRole.TYPE_ARGUMENT))
            .select((CtTypeReference<?> t) -> t.getTypeDeclaration() != null
                    && !t.getTypeDeclaration().isGenerics())
            .list();
  }
}
