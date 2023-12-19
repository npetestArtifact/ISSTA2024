package npetest.synthesizer.mutation;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtLocalVariableReference;

import java.util.ArrayList;
import java.util.List;

public class MutationUtil {
  private MutationUtil() {
  }

  public static int maxMutationTargetCount(CtStatementList statements) {
    return getCandidateTargets(statements).size();
  }

  public static List<CtCodeElement> getCandidateTargets(CtStatementList statements) {
    List<CtCodeElement> candidateTargets = new ArrayList<>();
    candidateTargets.addAll(getLiteralTargets(statements));
    candidateTargets.addAll(getObjectTargets(statements));
    return candidateTargets;
  }

  private static List<CtCodeElement> getObjectTargets(CtStatementList statements) {
    return statements.filterChildren((CtLocalVariable<?> lv) ->
            {
              if (statements.getStatements().indexOf(lv) == statements.getStatements().size() - 1) {
                return false;
              }

              if (lv.getType().isArray()) {
                return TypeUtils.isPrimitive(((CtArrayTypeReference<?>) lv.getType()).getComponentType());
              }

              return !TypeUtils.isNull(lv.getDefaultExpression().getType());
            })
            .list();
  }

  private static List<CtCodeElement> getLiteralTargets(CtStatementList statements) {
    return statements.filterChildren((CtExpression<?> expr) ->
                    expr instanceof CtLiteral<?> && !TypeUtils.isNull(expr.getType()))
            .list();
  }

  public static List<CtExpression<?>> getValuesUsedForArguments(CtStatementList statements) {
    return statements.filterChildren((CtExpression<?> expr) ->
                    expr.getRoleInParent().equals(CtRole.ARGUMENT) &&
                            !(expr instanceof CtLocalVariableReference<?>))
            .list();
  }
}
