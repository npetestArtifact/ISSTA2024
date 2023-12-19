package npetest.synthesizer.generators;

import npetest.analysis.npeanalysis.NPEAnalysisManager;
import npetest.commons.Configs;
import npetest.commons.filters.ConstantAccessFilter;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.sequence.TestCase;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.ObjectGenContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.search.value.ConstantFieldSearcher;
import npetest.synthesizer.search.value.LiteralSearcher;
import npetest.synthesizer.search.value.PredefinedLiteralPool;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.chain.CtQueryable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class consider boxing type as primitive type.
 * (different from TypeUtils class)
 */
public class PrimitiveExpressionGenerator {
  private PrimitiveExpressionGenerator() {
  }

  public static CtExpression<?> createRandomPrimitiveExpression(CtTypeReference<?> primitiveType) {
    if (Configs.TEST_CASE_MUTATION_STRATEGY.equals(Configs.TestCaseMutationStrategy.FEEDBACK) &&
            TestGenContext.getSeedTestCase() != null && !TypeUtils.isBoolean(primitiveType)) {
      CtExpression<?> ctExpression = generateExpressionsFromTrace(primitiveType);
      if (ctExpression == null) {
        return generateExpressionsFromScratch(primitiveType);
      } else {
        return ctExpression;
      }
    } else {
      return generateExpressionsFromScratch(primitiveType);
    }
  }

  private static CtExpression<?> generateExpressionsFromTrace(CtTypeReference<?> primitiveType) {
    Set<CtExpression<?>> candidateExpressions = new HashSet<>();
    TestCase seedTestCase = TestGenContext.getSeedTestCase();
    List<ExecutableKey> executableKeys = seedTestCase.getMethodTrace().get(seedTestCase.length() - 1);
    if (executableKeys == null) {
      return null;
    }
    List<CtExecutable<?>> queryRoots = executableKeys.stream()
            .filter(e -> NPEAnalysisManager.getWeight(e.toString()) != 0).map(ExecutableKey::getCtElement)
            .collect(Collectors.toList());
    for (CtExecutable<?> queryRoot : queryRoots) {
      candidateExpressions.addAll(queryRoot.filterChildren(ConstantAccessFilter.INSTANCE.setType(primitiveType))
              .map(CtElement::clone).list());
    }

    candidateExpressions.addAll(LiteralSearcher.getInstance().searchLiteralsWithTrace(TestGenContext.getSeedTestCase(), primitiveType));
    if (candidateExpressions.isEmpty()) {
      candidateExpressions.addAll(PredefinedLiteralPool.getLiterals(primitiveType));
    }
    List<CtExpression<?>> expressionsWithTypeCast = candidateExpressions.stream()
            .map(t -> t instanceof CtLiteral<?> && TypeUtils.isByte(t.getType())
                    ? (CtLiteral<?>) t.addTypeCast(TypeUtils.bytePrimitive()) : t)
            .collect(Collectors.toList());
    return RandomUtils.select(new HashSet<>(expressionsWithTypeCast));
  }

  private static CtExpression<?> generateExpressionsFromScratch(CtTypeReference<?> primitiveType) {
    CtPackage ctPackage = TestGenContext.getCtPackage();
    List<CtExpression<?>> candidateExpressions = new ArrayList<>(generateLiteralExpressions(primitiveType));
    Collection<CtField<?>> ctFields = ConstantFieldSearcher.getInstance().searchFields(ctPackage, primitiveType);
    candidateExpressions.addAll(ctFields.stream()
            .map(CodeFactory::createConstantFieldRead).collect(Collectors.toList()));

    if (candidateExpressions.isEmpty()) {
      candidateExpressions.addAll(PredefinedLiteralPool.getLiterals(primitiveType));
    }

    List<CtExpression<?>> expressionsWithTypeCast = candidateExpressions.stream()
            .map(t -> t instanceof CtLiteral<?> && TypeUtils.isByte(t.getType())
                    ? (CtLiteral<?>) t.addTypeCast(TypeUtils.bytePrimitive()) : t)
            .collect(Collectors.toList());

    return RandomUtils.select(expressionsWithTypeCast);
  }

  private static Collection<CtLiteral<?>> generateLiteralExpressions(CtTypeReference<?> primitiveType) {
    CtQueryable queryRoot = InvocationGenerationContext.peek();
    List<CtLiteral<?>> literals = new ArrayList<>();
    if (queryRoot != null) {
      literals.addAll(LiteralSearcher.getInstance().searchLiterals(queryRoot, primitiveType));
    }

    if (!literals.isEmpty()) {
      return literals;
    }

    queryRoot = ObjectGenContext.peek();
    if (queryRoot != null) {
      literals.addAll(LiteralSearcher.getInstance().searchLiterals(queryRoot, primitiveType));
    }

    if (!literals.isEmpty()) {
      return literals;
    }

    queryRoot = TestGenContext.getCtPackage();
    literals.addAll(LiteralSearcher.getInstance().searchLiterals(queryRoot, primitiveType));

    return literals;
  }
}
