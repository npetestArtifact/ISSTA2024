package npetest.language;

import npetest.commons.spoon.TypeAccessibilityChecker;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.*;
import spoon.reflect.code.CtComment.CommentType;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.*;

import java.util.List;

public class CodeFactory {
  private CodeFactory() {
  }

  private static Factory factory;

  public static void setup(Factory factory) {
    CodeFactory.factory = factory;
  }

  public static CtInvocation<?> createStaticMethodInvocation(CtMethod<?> targetMethod) {
    CtTypeAccess<?> typeAccess = createTypeAccess(targetMethod.getDeclaringType());
    return createInvocation(targetMethod, typeAccess);
  }

  public static CtInvocation<?> createInvocation(CtMethod<?> method, CtCodeElement accessTarget) {
    CtInvocation<?> invocation = factory.createInvocation();
    invocation.setValueByRole(CtRole.TARGET, accessTarget);
    CtExecutableReference<?> ctExecutableReference = factory.Executable().createReference(method);
    invocation.setValueByRole(CtRole.EXECUTABLE_REF, ctExecutableReference);
    return invocation;
  }

  public static <T> CtConstructorCall<? extends T> createConstructorCall(CtConstructor<? extends T> ctConstructor,
                                                                         CtTypeReference<? extends T> instanceType) {
    CtExecutableReference<? extends T> ctExecutableReference = factory.Executable().createReference(ctConstructor);
    CtConstructorCall<? extends T> ctConstructorCall = factory.createConstructorCall();
    ctConstructorCall.setValueByRole(CtRole.EXECUTABLE_REF, ctExecutableReference);
    ctConstructorCall.getType().setValueByRole(CtRole.TYPE_ARGUMENT, instanceType.getActualTypeArguments());
    return ctConstructorCall;
  }

  public static CtConstructorCall<?> createConstructorCall(CtConstructor<?> ctConstructor) {
    CtExecutableReference<?> ctExecutableReference = factory.Executable().createReference(ctConstructor);
    CtConstructorCall<?> ctConstructorCall = factory.createConstructorCall();
    ctConstructorCall.setValueByRole(CtRole.EXECUTABLE_REF, ctExecutableReference);
    return ctConstructorCall;
  }

  public static CtVariableAccess<?> createVariableAccess(CtLocalVariable<?> localVariable) {
    return factory.createVariableRead(localVariable.getReference(), false);
  }

  public static CtTypeAccess<?> createTypeAccess(CtType<?> ctType) {
    return factory.createTypeAccess(ctType.getReference(), false);
  }

  public static CtTypeAccess<?> createTypeAccess(CtTypeReference<?> ctTypeReference) {
    return factory.createTypeAccess(ctTypeReference, false);
  }

  public static <T> CtLocalVariable<T> createNewLocalVariable(CtExpression<T> rhsExpression) {
    String id = IdGenerator.generateIdentifier(rhsExpression.getType());
    return factory.createLocalVariable(rhsExpression.getType().clone(), id, rhsExpression);
  }

  public static CtLocalVariable<?> createNewLocalVariable(CtTypeReference<?> type, CtExpression<?> rhsExpression) {
    String id = IdGenerator.generateIdentifier(type);
    CtLocalVariable<?> localVariable = factory.createLocalVariable();
    localVariable.setValueByRole(CtRole.TYPE, type);
    localVariable.setSimpleName(id);
    localVariable.setValueByRole(CtRole.DEFAULT_EXPRESSION, rhsExpression);
    return localVariable;
  }

  public static CtNewArray<?> createNewArray(CtArrayTypeReference<?> arrayType) {
    CtNewArray<?> ctNewArray = factory.createNewArray();
    ctNewArray.setValueByRole(CtRole.TYPE, arrayType);
    return ctNewArray;
  }

  public static void setArrayElements(CtNewArray<?> newArray, List<? extends CtExpression<?>> arrayElements) {
    for (CtExpression<?> arrayElement : arrayElements) {
      arrayElement.setParent(newArray);
    }
    newArray.setValueByRole(CtRole.EXPRESSION, arrayElements);
  }

  public static CtTypeReference<?> createTypeReference(Class<?> aClass) {
    return factory.Type().createReference(aClass);
  }

  public static CtTypeReference<?> createTypeReference(String qualifiedTypeName) {
    return factory.Type().createReference(qualifiedTypeName);
  }

  public static CtLocalVariable<?> createNullVariable(CtTypeReference<?> instanceType) {
    CtLiteral<?> nullValue = createNullExpression(instanceType);
    return CodeFactory.createNewLocalVariable(instanceType, nullValue);
  }

  public static CtLiteral<?> createNullExpression(CtTypeReference<?> instanceType) {
    CtLiteral<?> nullValue = CodeFactory.createLiteral(null);
    nullValue.addTypeCast(instanceType);
    return nullValue;
  }

  public static <T> CtLiteral<?> createLiteral(T value) {
    return factory.createLiteral(value);
  }

  public static CtWildcardReference createWildcardReference() {
    return factory.createWildcardReference();
  }

  public static CtFieldRead<?> createFieldRead() {
    return factory.createFieldRead();
  }

  public static CtFieldRead<?> createConstantFieldRead(CtField<?> constantObject) {
    CtFieldRead<?> fieldRead = factory.createFieldRead();
    CtFieldReference<?> fieldReference = factory.Field().createReference(constantObject);
    CtTypeAccess<?> typeAccess = createTypeAccess(constantObject.getDeclaringType());
    fieldRead.setValueByRole(CtRole.TARGET, typeAccess);
    fieldRead.setValueByRole(CtRole.VARIABLE, fieldReference);
    return fieldRead;
  }

  public static CtFieldReference<?> createFieldReference(CtField<?> ctField) {
    return factory.Field().createReference(ctField);
  }

  public static CtFieldReference<?> createFieldReference() {
    return factory.createFieldReference();
  }

  public static CtStatementList createStatementList(CtStatement... statements) {
    CtStatementList statementList = factory.createStatementList();
    for (CtStatement statement : statements) {
      statementList.addStatement(statement);
    }
    return statementList;
  }


  public static CtVariableAccess<?> createVariableRead(CtLocalVariableReference<?> reference) {
    return CodeFactory.factory.createVariableRead(reference, false);
  }

  public static CtClass<?> createClass(String className) {
    return factory.Class().create(className);
  }

  public static CtCompilationUnit createCompilationUnit() {
    return factory.createCompilationUnit();
  }

  public static CtComment createComment(String comment) {
    return factory.createComment(comment, CommentType.JAVADOC);
  }

  public static CtFieldRead<?> createEnumRead(CtEnum<?> ctEnum, CtEnumValue<?> select) {
    CtFieldRead<?> enumValueRead = createFieldRead();
    CtFieldReference<?> enumValueReference = createFieldReference(select);
    CtTypeAccess<?> enumTypeAccess = createTypeAccess(ctEnum.getReference());
    enumValueRead.setValueByRole(CtRole.TARGET, enumTypeAccess);
    enumValueRead.setValueByRole(CtRole.VARIABLE, enumValueReference);
    return enumValueRead;
  }

  public static CtStatement wrapInvocationWithAssignee(CtInvocation<?> invocation,
                                                       CtTypeReference<?> returnType) {
    CtStatement instanceMethodCallStatement = null;
    if (!((CtTypedElement<?>) invocation).getType().equals(TypeUtils.voidPrimitive()) &&
            (returnType != null && TypeAccessibilityChecker.isGloballyAccessible(returnType.getTypeDeclaration()) &&
                    !(returnType instanceof CtTypeParameterReference))) {
      instanceMethodCallStatement = createNewLocalVariable(returnType, invocation);

    }
    if (instanceMethodCallStatement == null) {
      instanceMethodCallStatement = invocation;
    }
    return instanceMethodCallStatement;
  }
}