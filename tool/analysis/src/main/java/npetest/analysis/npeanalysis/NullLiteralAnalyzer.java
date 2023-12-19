package npetest.analysis.npeanalysis;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.exceptions.UnexpectedFailure;
import npetest.commons.keys.ParameterKey;
import npetest.commons.keys.TypeKey;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NullLiteralAnalyzer extends MethodAnalyzer {
  private static final Logger logger = LoggerFactory.getLogger(NullLiteralAnalyzer.class);
  private static final NullLiteralAnalyzer instance = new NullLiteralAnalyzer();

  private final Set<ParameterKey> nullableParameters = new HashSet<>();

  private final Map<String, Set<String>> nullableParamMap = new HashMap<>();

  // private final Set<CtElement> nullableLocalVariable = new HashSet<>();

  // private final Map<String, HashSet<CtElement>> nullableLocalVariable = new HashMap<>();
  private final Map<String, HashSet<CtLocalVariable<?>>> nullableLocalVariable = new HashMap<>();

  private final Map<String, Integer> nullableVariableAccessCounts = new HashMap<>();

  public static NullLiteralAnalyzer getInstance() {
    return instance;
  }

  @Override
  public void analyze(String methodKey) {
    /* 3. Check nullable field getter call*/
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null || nullableVariableAccessCounts.containsKey(methodKey)) {
      return;
    }

    List<CtLiteral<?>> nullRefs = ASTUtils.getAllNullLiterals(method);

    /* record nullable parameter (if null is passed to other methods) */
    for (CtLiteral<?> nullRef : nullRefs) {
      handleNullReference(method, nullRef);
    }

    /* 1. Check nullable variable access */
    int weight = 0;
    for (CtLiteral<?> nullRef : nullRefs) {
      if (nullRef.getRoleInParent().equals(CtRole.DEFAULT_EXPRESSION)) {
        CtLocalVariable<?> localVariable = nullRef.getParent(CtLocalVariable.class);
        weight += localVariable == null ? 0 : ASTUtils.countVariableAccess(method, localVariable);
      }
    }

    nullableVariableAccessCounts.put(methodKey, weight);
  }


  // private void handleNullReferenceTest(CtMethod<?> method, CtLiteral<?> nullRef) {
  //   logger.info("NULLREF: " + nullRef.toString());
  //   if (nullRef.getRoleInParent().equals(CtRole.ARGUMENT)) {
  //     logger.info("ARUGEMTN");
  //     handleNullPassingInvocation(nullRef);
  //   } else if (ASTUtils.isNullCheckingConditionForParameter(nullRef)) {
  //     logger.info("NullCheckingConditionFor Params");
  //     handleNullCheckingForParameter(method, nullRef);
  //   }
  // }

  private void handleNullReference(CtMethod<?> method, CtLiteral<?> nullRef) {
    if (nullRef.getRoleInParent().equals(CtRole.ARGUMENT)) {
      handleNullPassingInvocation(nullRef);
    } else if (ASTUtils.isNullCheckingConditionForParameter(nullRef)) {
      handleNullCheckingForParameter(method, nullRef);
    }
  }

  private void handleNullPassingInvocation(CtLiteral<?> nullRef) {
    CtElement parent = nullRef.getParent();
    if (parent instanceof CtAbstractInvocation<?>) {
      CtAbstractInvocation<?> invocation = (CtAbstractInvocation<?>) parent;
      int index = invocation.getArguments().indexOf(nullRef);
      CtExecutableReference<?> executableRef = invocation.getExecutable();
      CtExecutable<?> executable = executableRef.getExecutableDeclaration();
      if (executable instanceof CtMethod<?> && ((CtMethod<?>) executable).isPublic()
              && CtModelExt.INSTANCE.getCUTs().contains(TypeKey.of(((CtMethod<?>) executable).getDeclaringType()))) {
        nullableParameters.add(ParameterKey.of(executable, index));
      }
    }
  }

  private void handleNullCheckingForParameter(CtMethod<?> method, CtLiteral<?> nullRef) {
    if (isNullProperlyHandled(nullRef)) {
      return;
    }

    CtExpression<?> opposite = ASTUtils.getOppositeSideOfBinOp(nullRef);
    if (opposite == null) {
      throw new UnexpectedFailure("Contradict in traversing null checking expression in " + method.getSignature());
    }
    CtVariableReference<?> parameterReference = ((CtVariableRead<?>) opposite).getVariable();
    CtParameter<?> parameter = (CtParameter<?>) parameterReference.getDeclaration();
    int index = ASTUtils.getParameterIndex(method, parameter);
    if (index != -1) {
      nullableParameters.add(ParameterKey.of(method, index));
    }
  }

  private boolean isNullProperlyHandled(CtLiteral<?> nullRef) {
    if (nullRef.getParent() instanceof CtBinaryOperator &&
            ((CtBinaryOperator<?>) nullRef.getParent()).getKind().equals(BinaryOperatorKind.EQ) &&
            nullRef.getParent().getParent() instanceof CtIf) {
      CtIf ifStatement = (CtIf) (nullRef.getParent().getParent());
      CtStatement thenStatement = ifStatement.getThenStatement();
      return !thenStatement.filterChildren(new TypeFilter<>(CtThrow.class))
              .select((CtThrow th) -> th.getThrownExpression().getType().getSimpleName().equals("NullPointerException"))
              .list().isEmpty();
    }

    return false;
  }

  // find local variables that are nullabe (not primitive)
  public void setNullableVariable(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null) {
      return;
    }
    HashSet<CtLocalVariable<?>> tmpSet = new HashSet<>();

    List<CtLocalVariable<?>> localVariableList = method.filterChildren(new TypeFilter(CtLocalVariable.class)).list();

    for (CtLocalVariable<?> tmpVar : localVariableList) {
      CtTypeReference<?> ctTypeReference = tmpVar.getType();
      CtType<?> typeDeclaration = ctTypeReference.getTypeDeclaration();
      if (typeDeclaration == null) {
        if (ctTypeReference.isArray()) {
          tmpSet.add(tmpVar);
        }
      } else {
        if (!TypeUtils.isPrimitive(typeDeclaration)) {
          tmpSet.add(tmpVar);
        }
      }
    }

    this.nullableLocalVariable.put(methodKey, tmpSet);
  }

  // check the existance of nullable local variables
  // if yes, return true
  public boolean hasNullableLocalVariable(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null) {
      return false;
    }

    if (!nullableLocalVariable.containsKey(methodKey)) 
      setNullableVariable(methodKey);

    return !(this.nullableLocalVariable.get(methodKey).isEmpty());
  }

  public HashSet<CtLocalVariable<?>> getNullableLocalVariables(String methodKey) {
    if (!nullableLocalVariable.containsKey(methodKey)) 
      setNullableVariable(methodKey);

    return nullableLocalVariable.get(methodKey);
  }

  public Set<ParameterKey> getNullableParameters() {
    
    return nullableParameters;
  }

  private void setNullableParamMap(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    List<CtParameter<?>> params = method.getParameters();
    Set<String> tmp = new HashSet<String>();

    for (CtParameter<?> param : params) {
      if (!TypeUtils.isPrimitive(param.getType())) {
        tmp.add(param.getSimpleName());
      }
    }

    nullableParamMap.put(methodKey, tmp);
  }


  public Set<String> getNullableParams(String methodKey) {
    if (!nullableParamMap.containsKey(methodKey)) setNullableParamMap(methodKey);

    return nullableParamMap.get(methodKey);
  }

  public int getScore(String methodKey) {
    return nullableVariableAccessCounts.getOrDefault(methodKey, 0);
  }

  public boolean checkNullableParameter(CtExecutable<?> executable, int i) {
    return nullableParameters.contains(ParameterKey.of(executable, i));
  }
}
