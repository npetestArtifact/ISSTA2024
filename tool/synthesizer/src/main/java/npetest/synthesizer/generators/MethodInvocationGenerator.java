package npetest.synthesizer.generators;

import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.typeadaption.TypeConcretizer;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class MethodInvocationGenerator {
  private MethodInvocationGenerator() {
  }

  /**
   * @param receiver   CtTypeAccess (resp. CtVariableAccess) for static (resp. instance) method invocation
   * @param returnType
   */
  public static Sequence generateMethodInvocation(CtMethod<?> method, CtExpression<?> receiver,
                                                  CtTypeReference<?> receiverType, CtTypeReference<?> returnType) {
    InvocationGenerationContext.add(method);
    CtInvocation<?> invocation = CodeFactory.createInvocation(method, receiver);
    List<CtTypeReference<?>> methodTypeArguments = TypeConcretizer.setupTypeArgumentsOfExecutable(method);
    List<VariableType> inputTypes = TypeConcretizer.setupInputTypes(method, invocation, receiverType, methodTypeArguments);
    boolean emptyReturn = returnType == null;
    CtStatementList result = CodeFactory.createStatementList();
    returnType = emptyReturn
            ? TypeConcretizer.concretizeReturnTypeOfInvocation(invocation, method, receiverType)
            : returnType;
    CtStatement invocationStatement = emptyReturn
            ? CodeFactory.wrapInvocationWithAssignee(invocation, returnType)
            : CodeFactory.createNewLocalVariable(returnType, invocation);
    List<CtExpression<?>> arguments = new ArrayList<>();
    for (VariableType variableType : inputTypes) {
      Sequence argumentCreationSequence = ObjectInstantiator.instantiate(variableType, false);
      if (!argumentCreationSequence.isInlineValue()) {
        for (CtStatement ctStatement : argumentCreationSequence.getCtStatementList()) {
          result.addStatement(ctStatement);
        }
      }
      CtExpression<?> argument = argumentCreationSequence.getAccessExpression();
      arguments.add(argument);
    }

    invocation.setArguments(arguments);
    result.addStatement(invocationStatement);
    InvocationGenerationContext.remove(method);
    return new Sequence(result);
  }
}
