package npetest.analysis.npeanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import npetest.analysis.complexityanalysis.ComplexityAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.misc.Timer;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class PathAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(PathAnalyzer.class);

  private static final PathAnalyzer instance = new PathAnalyzer();

  String typeKey = "";
  String targetClass = "";

  private Set<String> doneReturnAnalysis = new HashSet<>();
  private Set<String> donePathAnalysis = new HashSet<>();
  private int totalPaths = 0;
  private int totalComplexity = 0;

  private int totalFieldWrite = 0;

  private Set<String> targetMethodSet = new HashSet<>();

  private Set<String> doneComputeScore = new HashSet<>();

  SourcePosition startPosition = null;


  // Struct for path information in each method
  private static class MethodPath {
    // private Set<CtField<?>> fields;
    // private Set<CtElement> npeObject;
    private boolean returnNull = false;
    private boolean hasNPEPath = true;

    private float score;


    private Set<String> invoCalls = new HashSet<>();
    private Set<List<CtElement>> returnPaths = new HashSet<>();
    private Set<List<CtElement>> mayNullPaths = new HashSet<>();

    private Set<List<CtElement>> nonNullPaths = new HashSet<>();

    private Set<String> readField = new HashSet<>();
    private Set<String> writtenField = new HashSet<>();
    private Set<String> impureMethod = new HashSet<>();

    private Map<String, Integer> numWrittenField = new HashMap<>();

    private List<CtElement> mayNPEstmt = new ArrayList<>();

    private int complex;

    public void setComplexity(int complex) {
      this.complex = complex;
    }

    public int getComplexity() {
      return this.complex;
    }

    public Set<String> getReadField() {
      return readField;
    }

    public void addWrittenField(String field, int i) {
      int tmp = 0;
      if (numWrittenField.containsKey(field)) tmp = numWrittenField.get(field);
      numWrittenField.put(field, tmp + i);
    }

    public int getNumWrittenField(String field) {

      if (!numWrittenField.containsKey(field)) return 0;
      return numWrittenField.get(field);
    }

    public void setReadField(Set<String> result) {
      readField = new HashSet<>(result);
    }

    public Set<String> getWrittenField() {
      return writtenField;
    }

    public void setWrittenField(Set<String> result) {
      writtenField = new HashSet<>(result);
    }

    public void addImpureMethod(String methodKey) {
      impureMethod.add(methodKey);
    }

    public Set<String> getImpureMethod() {
      return impureMethod;
    }

    public int getNumPath() {
      return mayNullPaths.size() + nonNullPaths.size();
    }

    public void setReturnNull(boolean b) {
      this.returnNull = b;
    }

    public boolean getReturnNull() {
      return returnNull;
    }

    public void setNPEPath(boolean b) {
      this.hasNPEPath = b;
    }

    public void setNPEStmt(List<CtElement> tmp) {
      mayNPEstmt.addAll(tmp);
    }

    public boolean getNPEPath() {
      return this.hasNPEPath;
    }

    public void setReturnPath(Set<List<CtElement>> paths) {
      returnPaths.addAll(paths);
      // returnPaths = new HashSet<>(paths);
    }

    public void setMayNullPath(Set<List<CtElement>> paths) {
      mayNullPaths.addAll(paths);
      // mayNullPaths = new HashSet<>(paths); 
    }

    public Set<List<CtElement>> getReturnPath() {
      return returnPaths;
    }

    public Set<List<CtElement>> getNonNullPaths() {
      return nonNullPaths;
    }

    public int getNonNullPathNum() {
      return nonNullPaths.size();
    }

    public void addMayNullPaths(List<CtElement> path) {
      mayNullPaths.add(path);
    }

    public void removeMayNullPaths(List<CtElement> path) {
      mayNullPaths.remove(path);

    }

    public void addNonNullPaths(List<CtElement> path) {
      nonNullPaths.add(path);
    }

    public Set<List<CtElement>> getMayNullPath() {
      return mayNullPaths;
    }


    public float computeScore(int totalPath) {
      float score = 0f;
      int nullablePaths = mayNullPaths.size();
      if (nullablePaths > 0)
        score = ((float)nullablePaths)/((float)totalPath);
      else if (complex > 10 && !mayNPEstmt.isEmpty()) {
        score = ((float) mayNPEstmt.size())/((float)totalPath);
      }
      return score;
    }

    public float getImpureScore(int totalFieldWrite) {
      if (!numWrittenField.isEmpty()) {
        int score = 0;
        for (String key : numWrittenField.keySet()) {
          score += numWrittenField.get(key);
        }

        return ((float)score / (float) totalFieldWrite);
      }
      else return 0;
    }

    public void setScore(float score) {
      if (score == 0) this.score = 0.01f;
      else this.score = score;
    }

    public void addInvoCalls(String methodKey) {
      invoCalls.add(methodKey);
    }

    public Set<String> getInvoCalls() {
      return invoCalls;
    }

    public float getScore() {

      return this.score;
    }
  }

  private Map<String, MethodPath> methodPathMap = new HashMap<>();

  public Set<String> getImpureMethods(String methodKey) {
    return methodPathMap.get(methodKey).getImpureMethod();
  }

  // 
  public float getRelatedScore(String methodKey, String targetKey) {
    float score = 0;

    if (methodPathMap.get(methodKey).getImpureMethod().contains(targetKey)) {
      Set<String> readFields = methodPathMap.get(methodKey).getReadField();
      if (!readFields.isEmpty()) {
        for (String field : readFields) {
          score += methodPathMap.get(targetKey).getNumWrittenField(field);
        }        
      }
    }
    return score;
  }
  
  private void computeScore(String methodKey) {
    if (doneComputeScore.contains(methodKey)) return;
    // float score = ((float)methodPathMap.get(methodKey).getMayNullPath().size())/((float)totalPaths);
    float score = methodPathMap.get(methodKey).computeScore(totalPaths);
    // score = ComplexityAnalyzer.getInstance().getScore(methodKey) == 0 ? score : ComplexityAnalyzer.getInstance().getScore(methodKey) * score;

    Set<String> invoCalls = methodPathMap.get(methodKey).getInvoCalls();

    if (invoCalls != null) {
      // logger.debug("COMPUTE SCORE: " + methodKey);
      for (String method : invoCalls) {
        // logger.debug("COMPUTE invo SCORE: " + method);
        if (method.equals(methodKey)) continue;
        float tmpScore = methodPathMap.get(method).getScore();
        if (tmpScore == 0) {
          computeScore(method);
        }
        score += methodPathMap.get(method).getScore();
      }
    }

    if (score != 0)
      score += totalComplexity == 0 ? 0 : ((float) ComplexityAnalyzer.getInstance().getScore(methodKey)) / ((float)totalComplexity);

    methodPathMap.get(methodKey).setScore(score);
    doneComputeScore.add(methodKey);
  }

  public static PathAnalyzer getInstance() {
    return instance;
  }

  // Return false, when all paths to the return statements result in non-null value;
  public boolean returnNonNull(String methodKey) {
    return methodPathMap.get(methodKey).getReturnNull();
  }

  public float getImpureScore(String methodKey) {
    return methodPathMap.get(methodKey).getImpureScore(totalFieldWrite);
  }

  public boolean hasNPEPath(String methodKey) {
    return methodPathMap.get(methodKey).getNPEPath();
  }

  Set<String> doneFieldAccess = new HashSet<>();
  Set<String> doneFieldWrite = new HashSet<>();

  private void setFieldAccess(String methodKey, int flag) {
    if (doneFieldAccess.contains(methodKey) || flag > 1) return;
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    Set<String> result = new HashSet<String>();

    List<CtFieldRead<?>> tmpList = method.filterChildren(new TypeFilter(CtFieldRead.class))
          .select(e -> {
            CtElement eleParent = e.getParent();
            if (eleParent instanceof CtInvocation) {
              String methodCall = ((CtExecutableReference<?>)((CtInvocation<?>)eleParent).getExecutable()).getSignature();
              return (methodCall.contains("get") || methodCall.contains("contain") || methodCall.contains("equal"));
            }
            return (eleParent instanceof CtConstructorCall);
            // e.getParent() instanceof CtInvocation || e.getParaent() instanceof CtConstructorCall || e.getParent()
          })
          .list();

    for (CtFieldRead<?> tt : tmpList) {
      result.add(tt.toString());
    }

    Set<String> invocalls = methodPathMap.get(methodKey).getInvoCalls();

    if (!invocalls.isEmpty()) {
      for (String key : invocalls) {
        if (!doneFieldAccess.contains(key)) setFieldAccess(key, flag + 1);
        result.addAll(methodPathMap.get(key).getReadField());
      }
    }

    methodPathMap.get(methodKey).setReadField(result);

    doneFieldAccess.add(methodKey);
  }

  private void setFieldWrite(String methodKey, int flag) {
    if (doneFieldWrite.contains(methodKey) || flag > 1) return;
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    Set<String> result = new HashSet<String>();
    // logger.debug("IMPURE FIELD: " + methodKey);

    List<CtFieldRead<?>> tmpList = method.filterChildren(new TypeFilter(CtFieldRead.class))
          .select(e -> {
            CtElement eleParent = e.getParent();
            if (eleParent instanceof CtInvocation) {
              String methodCall = ((CtExecutableReference<?>)((CtInvocation<?>)eleParent).getExecutable()).getSignature();
              return (methodCall.contains("put") || methodCall.contains("add") || methodCall.contains("remove") || methodCall.contains("set"));
            }
            return false;
            // e.getParent() instanceof CtInvocation || e.getParaent() instanceof CtConstructorCall || e.getParent()
          })
          .list();

    Set<String> invocalls = methodPathMap.get(methodKey).getInvoCalls();

    for (CtFieldRead<?> tt : tmpList) {
      result.add(tt.toString());
      methodPathMap.get(methodKey).addWrittenField(tt.toString(), 1);
      totalFieldWrite++;
    }

    if (!invocalls.isEmpty()) {
      for (String key : invocalls) {
        if (!doneFieldWrite.contains(key)) setFieldWrite(key, flag + 1);
        result.addAll(methodPathMap.get(key).getWrittenField());

        Set<String> tmpSet = methodPathMap.get(key).getWrittenField();

        if (!tmpSet.isEmpty()) {
          for (String fieldKey : tmpSet) {
            int tmp = methodPathMap.get(key).getNumWrittenField(fieldKey);
            methodPathMap.get(methodKey).addWrittenField(fieldKey, tmp);
          }
        }        
      }
    }

    methodPathMap.get(methodKey).setWrittenField(result);
    doneFieldWrite.add(methodKey);
  }

  private boolean isPrivate(String methodKey) {
    CtMethod method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    return method.isPrivate();
  }

  private void setImpureMethod(Set<String> methods) {
    for (String methodKey : methods) {

      Set<String> tmpReadField = methodPathMap.get(methodKey).getReadField();

      // logger.debug("READ KEY: " + methodKey);
      // logger.debug(tmpReadField.toString());

      for (String key : methods) {
        if(methodKey.equals(key)) continue;
        Set<String> tmpWrittenField = new HashSet<>(methodPathMap.get(key).getWrittenField());

        // logger.debug("WRITTEN FILED :" + key);
        // logger.debug(tmpWrittenField.toString());

        tmpWrittenField.retainAll(tmpReadField);

        if (!tmpWrittenField.isEmpty() && !isPrivate(key)) {
          methodPathMap.get(methodKey).addImpureMethod(key);
        } 
      }

      // logger.debug("IMPRUE METHOD: " + methodKey);
      // logger.debug(methodPathMap.get(methodKey).getImpureMethod().toString());
    }
  }

  public float getScore(String methodKey) {

    if (!methodKey.contains("#")) {
      if (!methodPathMap.containsKey(targetClass + "#" + methodKey)) return ((float)1)/((float)100);
      return methodPathMap.get(targetClass + "#" + methodKey).getScore();
    }

    if (!methodPathMap.containsKey(methodKey)) return ((float)1)/((float)100);
    return methodPathMap.get(methodKey).getScore();
  }

  private void calculateScore(String methodKey) {

    computeScore(methodKey);

    methodPathMap.get(methodKey).computeScore(totalPaths);
  }

  // returns true when there exists any path which makes the target null.
  // returns false when all paths do not reach to null.
  // flag == 0 : from return path, flag == 1: from nullable path
  private boolean checkNullableTarget(String methodKey, Set<List<CtElement>> paths, int flag) {
    boolean result = false;
    CtElement next = null;

    CtLiteral<?> trueCond = CodeFactory.createLiteral(true);
    CtLiteral<?> falseCond = CodeFactory.createLiteral(false);
    CtLiteral<?> nullStmt = CodeFactory.createLiteral(null);

    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    // logger.debug("CHECK NULLABLE PATHS: " + methodKey);
    // for (List<CtElement> path : paths) {
    //   logger.debug(path.toString());
    // }

    Set<String> nullableParams = NullLiteralAnalyzer.getInstance().getNullableParams(methodKey);

    
    Map<String, Boolean> paramVarMap = new HashMap<>();

    // logger.debug("PARAMS");
    for (String pp : nullableParams) {
      // logger.debug(pp);
      paramVarMap.put(pp, true);
    }

    // for (ParameterKey tt : nullableParams) {    
    //   method.getParameters()
    //   paramVarMap.put(tt.toString(), true);
    // }

    typeKey = method.getTopLevelType().getQualifiedName();    

    for (List<CtElement> path : paths) {
      // lastElement is the target expression (CtExpression<?> type)
      // logger.debug("PATH TESTING START");
      // logger.debug(path.toString());

      // targetVar is the Expression which we track for the nullablility
      CtExpression<?> targetVar = (CtExpression<?>) path.get(path.size() - 1);
      CtElement lastEleInPath = path.get(path.size() - 2);
      Map<String, Boolean> nullableVar = new HashMap<>();
      nullableVar.putAll(paramVarMap);

      // if (NullableFieldAnalyzer.getInstance().isNonNullable(targetVar.toString())) {
      //   // logger.debug("NON NULLABLE VAR");
      //   nullableVar.put(targetVar.toString(), false);
      // }
      // else nullableVar.put(targetVar.toString(), true);


      nullableVar.put(targetVar.toString(), true);


      // Heuristics... 
      // If the last element is something like A.B.C.D. ... (we assume that the path results in null)
      if (lastEleInPath.filterChildren(new TypeFilter(CtInvocation.class)).list().size() > 3) {
        result = true;
        continue;
      }


      if (targetVar instanceof CtFieldRead) {
        logger.debug("TARGET IS FROM FIELD: " + targetVar.toString());
        nullableVar.put(targetVar.toString(), NullableFieldAnalyzer.getInstance().isNullable(typeKey, targetVar.toString()));
      }

      ListIterator<CtElement> iter = path.listIterator();

      // Check whether the target expression is a method call which always returns non-null;
      if (targetVar instanceof CtInvocation) {
        CtExecutableReference<?> methodCall = ((CtInvocation<?>)targetVar).getExecutable();
        String tmpKey = typeKey + "#" + methodCall.getSignature();

        if (!methodPathMap.containsKey(tmpKey)) analysisReturnPath(methodKey);


        if (methodPathMap.containsKey(tmpKey)) {

            // logger.debug("NOQRQR: " + targetVar.toString());

          if (!doneReturnAnalysis.contains(tmpKey)) {
            // logger.debug("YESESE");
            analysisReturnPath(tmpKey);
          }

          logger.debug(Boolean.toString(methodPathMap.get(tmpKey).getReturnNull()));


          result |= methodPathMap.get(tmpKey).getReturnNull();
          
          if (!methodPathMap.get(tmpKey).getReturnNull()) {
            // logger.debug("THIS TARGET RETURNS NON NULL? " );
            // logger.debug(path.toString());
            methodPathMap.get(methodKey).addNonNullPaths(path);
          }

          continue;
        }
      }
      
      while (iter.hasNext()) {
        next = iter.next();

        if (next.equals(targetVar)) continue;

        if (next instanceof CtLocalVariable) {
          // printSelectedEle(next);
          CtElement lhs = ((CtLocalVariable<?>) next).getReference();
          CtExpression<?> rhs = ((CtLocalVariable<?>) next).getAssignment();

          if (rhs == null) continue;

          if (TypeUtils.isPrimitive(rhs.getType())) continue;

          if (rhs instanceof CtConstructorCall) {
            // logger.debug(lhs.toString());

            nullableVar.put(lhs.toString(), false);


            List<CtInvocation<?>> tmpInvList = rhs.filterChildren(new TypeFilter(CtInvocation.class)).list();
            if (!tmpInvList.isEmpty()) {

              for (CtInvocation<?> tt : tmpInvList) {
                CtExecutableReference<?> callName = tt.getExecutable();
                String tmpKey = typeKey + "#" + callName.getSignature();

                if (targetMethodSet.contains(tmpKey)) {
                  if (!donePathAnalysis.contains(tmpKey)) 
                    analysisNullablePath(tmpKey);

                  if (flag == 0) {
                    methodPathMap.get(methodKey).addMayNullPaths(path);
                  } else {
                    result = true;
                    break;    
                  }
                }
                  // if (methodPathMap.get(tmpKey).getNPEPath()) {
                  //   if (flag == 0) {
                  //     methodPathMap.get(methodKey).addMayNullPaths(path);
                  //   } else {
                  //     result = true;
                  //     break;    
                  //   }
                  // }
              }
            }

          } else if(rhs instanceof CtInvocation) {
            CtExecutable<?> calledInvocation = ((CtInvocation<?>) rhs).getExecutable().getExecutableDeclaration();

            String calledInvocationName = 
              calledInvocation == null ? typeKey + "#" + ((CtInvocation<?>) rhs).getExecutable().getSignature() :
                                         typeKey + "#" + calledInvocation.getSignature();


            if (!methodPathMap.containsKey(calledInvocationName)) nullableVar.put(lhs.toString(), true);
            else if (doneReturnAnalysis.contains(calledInvocationName)) nullableVar.put(lhs.toString(), methodPathMap.get(calledInvocationName).getReturnNull());
            else {
              nullableVar.put(lhs.toString(), methodPathMap.get(calledInvocationName).getReturnNull());
            }

          } else if (rhs instanceof CtVariableRead) {

            if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
            else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));

            // logger.debug("RHS CTVARIABLE");
            // logger.debug(targetVar.toString());
            // logger.debug(rhs.toString());
            // setTargetRelated(methodKey, targetVar.toString(), rhs.toString());
            // setTargetRelated(methodKey, targetVar.toString(), targetVar.toString());

          } else if (rhs.equals(nullStmt)) {
            nullableVar.put(lhs.toString(), true);
          } else {
            nullableVar.put(lhs.toString(), true);
          }

        } else if (next instanceof CtAssignment) {
          // printSelectedEle(next);
          CtCodeElement rhs = ((CtAssignment<?, ?>) next).getValueByRole(CtRole.ASSIGNMENT);
          CtCodeElement lhs = ((CtAssignment<?, ?>) next).getValueByRole(CtRole.ASSIGNED);

          // if (isRelatedToTarget(methodKey, (CtExpression<?>)lhs, tmpTargetVar)) tmpTargetVar = (CtExpression<?>)lhs;
          

          if (TypeUtils.isPrimitive(((CtExpression<?>)lhs).getType())) continue;

          if (rhs instanceof CtConstructorCall) {
            nullableVar.put(lhs.toString(), false);
          } else if (rhs instanceof CtInvocation) {
            String calledInvocationName = typeKey + "#" + ((CtInvocation<?>) rhs).getExecutable().getSignature();


            if (!methodPathMap.containsKey(calledInvocationName)) nullableVar.put(lhs.toString(), true);
            else if (doneReturnAnalysis.contains(calledInvocationName)) nullableVar.put(lhs.toString(), methodPathMap.get(calledInvocationName).getReturnNull());
            else {
              nullableVar.put(lhs.toString(), methodPathMap.get(calledInvocationName).getReturnNull());
            }

          } else if (rhs instanceof CtVariableRead) {

            if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
            else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));
            // logger.debug("RHS CTVARIABLE2");
            // logger.debug(targetVar.toString());
            // logger.debug(rhs.toString());
            // setTargetRelated(methodKey, targetVar.toString(), rhs.toString());
            // setTargetRelated(methodKey, targetVar.toString(), targetVar.toString());

          } else if (rhs.equals(nullStmt)) {
            nullableVar.put(lhs.toString(), true);
          }

        } else if (next instanceof CtInvocation) {
          CtExecutableReference<?> callName = ((CtInvocation<?>) next).getExecutable();
          String tmpKey = typeKey + "#" + callName.getSignature();
          
          // logger.debug("TESTING METHOD? in CtInvocation:" + methodKey);
          // logger.debug(next.toString());
          // logger.debug(tmpKey);

          if (targetMethodSet.contains(tmpKey)) {
            if (!donePathAnalysis.contains(tmpKey)) 
              analysisNullablePath(tmpKey);

            
            if (flag == 1) {
              result = true;
              break;
            }
          }

          
        } else if (next instanceof CtReturn) {
          CtExpression<?> returnedExp = ((CtReturn<?>)next).getReturnedExpression();
          logger.debug("TESTING RETURNED METHOD? CTRETURN:" + methodKey);
          logger.debug(targetVar.toString());
          // logger.debug(nullableVar.get(targetVar.toString()).toString());
          printSelectedEle(next);
        }
        
        else if (next.equals(trueCond) || next.equals(falseCond)) { // Conditional statment in if/for/while ...
          CtElement boolFlag = next;
          next = iter.next(); 

          // nullableVar.put(tmpTargetVar.toString(), true);

          // if (next instanceof CtInvocation) {
          //   logger.debug("AFTER COND ITS CTINVOCATION");
          //   logger.debug(next.toString());
          // }

          nullableVar = handlingNullCond(next, nullableVar, boolFlag);
        } 
      }

      // logger.debug("END OF PATH");
      // logger.debug(path.toString());
      // logger.debug(targetVar.toString());
      // logger.debug(nullableVar.toString());

      if (!nullableVar.get(targetVar.toString()) && flag == 1) {
        methodPathMap.get(methodKey).addNonNullPaths(path);
        methodPathMap.get(methodKey).removeMayNullPaths(path);
      }

      result |= nullableVar.get(targetVar.toString());
    }

    // logger.debug("RESULT OF : " + methodKey);
    // logger.debug(Boolean.toString(result));

    return result;
  }

  // we only have interests in conditional statment which contains the variables related to target
  // if not, it comes from foreach statment, which we dont handle.
  // return true if the condition successfully handles null for the target
  // TODO
  private Map<String, Boolean> handlingNullCond(CtElement cond, Map<String, Boolean> prevMap, CtElement bool) {
    CtLiteral<?> trueCond = CodeFactory.createLiteral(true);
    CtLiteral<?> falseCond = CodeFactory.createLiteral(false);    

    if (cond instanceof CtBinaryOperator) {
      CtElement tmpVar = null;
      List<CtElement> tmpVarList = cond.filterChildren(new TypeFilter(CtVariableRead.class)).list();

      if (tmpVarList.size() == 1) tmpVar = tmpVarList.get(0);
      else return prevMap;

      if (((CtBinaryOperator<?>)cond).getKind().equals(BinaryOperatorKind.EQ) && bool.equals(falseCond) ||
          ((CtBinaryOperator<?>)cond).getKind().equals(BinaryOperatorKind.NE) && bool.equals(trueCond)) {
        prevMap.put(tmpVar.toString(), false);
      } else if (((CtBinaryOperator<?>)cond).getKind().equals(BinaryOperatorKind.EQ) && bool.equals(trueCond) ||
                ((CtBinaryOperator<?>)cond).getKind().equals(BinaryOperatorKind.NE) && bool.equals(falseCond)) {
        prevMap.put(tmpVar.toString(), true);
      } 
    }

    return prevMap;
  }

  private void analysisReturnPath(String methodKey) {
    if (doneReturnAnalysis.contains(methodKey)) return;

    // Map <String, Set<List<CtElement>>> returnedPaths = returnPaths;

    logger.debug("STARTS ANALYSIS RETURN PATH: " + methodKey);
    // logger.debug(methodKey);
    doneReturnAnalysis.add(methodKey);

    if (methodPathMap.get(methodKey).getReturnPath().isEmpty()) {
      setReturnNull(methodKey, true);
      return;
    }


    // if (checkNullableTarget(methodKey, methodPathMap.get(methodKey).getReturnPath())) {
    if (checkNullableTarget(methodKey, new HashSet<>(methodPathMap.get(methodKey).getReturnPath()), 0)) {
    // logger.debug("THIS METHOD CAN RETURN NULL: " + methodKey);
      setReturnNull(methodKey, true);
    } else {
      // logger.debug("FALSE RETURN VAL :" + methodKey);
      setReturnNull(methodKey, false);
    }    
  }

  private void analysisNullablePath(String methodKey) {
    logger.debug("NULLABLE PATH ANALSYSI STARTS: " + methodKey);
    // if (methodPathMap.get(methodKey).getMayNullPath().isEmpty()) return;
    if (methodPathMap.get(methodKey).getMayNullPath().isEmpty() || donePathAnalysis.contains(methodKey)) return;
    donePathAnalysis.add(methodKey);

    logger.debug("-- analysis NULLABLE PATH: COLLECTED NULLABLE PATH: " + Integer.toString(methodPathMap.get(methodKey).getNumPath()));

    // logger.debug("MAY NULL PATH LIST");
    // for (List<CtElement> path : methodPathMap.get(methodKey).getMayNullPath()) {
    //   logger.debug(path.toString());
    // }

    // IF the given method has any path reaching to NPE
    // we set the value to false
    if (!checkNullableTarget(methodKey, new HashSet<>(methodPathMap.get(methodKey).getMayNullPath()), 1)) {
      logger.debug("THIS PATH HAS NO NPE PATH: " + methodKey);
      setHasNPEPath(methodKey, false);
    } else setHasNPEPath(methodKey, true);
  }

  private void analysisNPEMethod(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    List<CtElement> mayNPEStmts = method.filterChildren(new TypeFilter<>(CtInvocation.class))
          .select((CtInvocation<?> call) -> !(call instanceof CtAssignment) || !(call instanceof CtLocalVariable))
          .select((CtInvocation<?> call) -> !(call.getParent().getParent() instanceof CtCatch))
          .select((CtInvocation<?> call) -> !(call.getExecutable() == null))
          .select((CtInvocation<?> call) -> {
              List<CtInvocation<?>> tmp = call.filterChildren(new TypeFilter(CtInvocation.class)).list();
              return (tmp.size() < 2);
              })    // To limit the size of the paths... (TODO: more precise)
          .list();

    logger.debug("do analsysisNPEMethod: " + methodKey);
    for (CtElement call : mayNPEStmts) {
      logger.debug(call.toString());

      CtExecutableReference<?> methodCall = ((CtInvocation<?>)call).getExecutable();
      String tmpKey = typeKey + "#" + methodCall.getSignature();
      logger.debug(tmpKey);

      if (!methodPathMap.containsKey(tmpKey)) continue;

      if (!donePathAnalysis.contains(tmpKey)) {
        analysisNullablePath(tmpKey);
      }

      methodPathMap.get(methodKey).addInvoCalls(tmpKey);

      if (methodPathMap.get(tmpKey).getNPEPath()) {
        setHasNPEPath(methodKey, true);
        return;
      }
    }
  }


  private CtBlock<?> getBodyBlk(CtElement e) {
    CtElement parent = e.getParent();
    if (!(parent instanceof CtBlock)) return getBodyBlk(parent);
    else return ((CtBlock<?>) parent);
  }

  private CtElement getOuterCtInvocation(CtElement e) {
    CtElement parent = e.getParent();

    if (parent instanceof CtBlock) return e;
    else return getOuterCtInvocation(parent);
  }


  // gather all paths to the return statements
  // to check whether the given method must not return nonnull. 
  private void methodReturns(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    CtTypeReference<?> returnType = method.getType();

    // Given methods does not have return value    
    if (TypeUtils.isVoidPrimitive(returnType) || TypeUtils.isPrimitive(returnType)) return;

    List<CtReturn<?>> returnEles = method.filterChildren(new TypeFilter<>(CtReturn.class))
      .select(e -> ((CtReturn<?>)e).getReturnedExpression() != null)
      .list();    

    logger.debug("CHECK RETURN PATH for the method : " + methodKey);

    // check whether there exists any return stmt which directly returns null

    if (returnEles.stream().filter(e -> TypeUtils.isNull(e.getReturnedExpression().getType())).count() != 0) {
      setReturnNull(methodKey, true);

      return;
    }

    List<CtElement> initialList = new ArrayList<>();

    initialList.add(CodeFactory.createLiteral(null));

    int complexity = ComplexityAnalyzer.getInstance().getScore(methodKey);

    methodPathMap.get(methodKey).setComplexity(complexity);

    if (complexity > 10) {
      methodPathMap.get(methodKey).setReturnNull(true);
      return;
    }

    for (CtReturn<?> tt : returnEles) {
      CtExpression<?> targetExp = tt.getReturnedExpression();
      
      // Lightly analysis... 
      if (targetExp instanceof CtInvocation) {
        setReturnNull(methodKey, true);
        return;
      } else if (targetExp instanceof CtVariableRead) {
        CtBlock<?> block = method.getBody();

        CtElement startPoint = block.getDirectChildren().get(0);
        startPosition = startPoint.getPosition();

        PathBuilder.getInstance().setConstruct(false);
        PathBuilder.getInstance().setStartPosition(startPosition);
        PathBuilder.getInstance().calculatePath(methodKey, block, startPoint, targetExp, initialList);

        Set<List<CtElement>> pathResult = PathBuilder.getInstance().getPathList();
        methodPathMap.get(methodKey).setReturnPath(pathResult);
        
        // pathFromStart(methodKey, block, startPoint, targetExp, initialList, 0);
      }
    }
  }

  private void methodPaths(String methodKey) {

    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    List<CtElement> methodCall = null;


    // logger.debug("GET TARGET TEST");
    // We only maintain the nullable statments.. may NPE 

    logger.debug("FINDING METHOD PATHS: " + methodKey);

    List<CtElement> mayNPEStmts = method.filterChildren(new TypeFilter<>(CtInvocation.class))
          .select((CtInvocation<?> call) -> {
              List<CtElement> tmpEles = call.getDirectChildren();
              // return tmpEles.stream().filter(e -> e instanceof CtThisAccess || e instanceof CtTypeAccess || e instanceof CtInvocation).count() == 0;
              // return tmpEles.stream().filter(e -> e instanceof CtThisAccess || e instanceof CtTypeAccess).count() == 0;
              // return !(tmpEles.get(0) instanceof CtThisAccess);
              return (!(tmpEles.get(0) instanceof CtThisAccess) && tmpEles.stream().filter(e -> e instanceof CtTypeAccess).count() == 0);
            })
          .select((CtInvocation<?> call) -> !(call instanceof CtAssignment) || !(call instanceof CtLocalVariable))
          .select((CtInvocation<?> call) -> !(call.getParent().getParent() instanceof CtCatch))
          .select((CtInvocation<?> call) -> !(call.getExecutable() == null))
          .select((CtInvocation<?> call) -> {
              List<CtInvocation<?>> tmp = call.filterChildren(new TypeFilter(CtInvocation.class)).list();
              return (tmp.size() < 3);
              })    // To limit the size of the paths... (TODO: more precise)
          .list();
          
    List<CtElement> boxingStmts = method.filterChildren(new TypeFilter<>(CtLocalVariable.class))
              .select((CtLocalVariable<?> e) -> {
                logger.debug("boxing check");
                printSelectedEle(e);
                CtLocalVariableReference<?> lhs = ((CtLocalVariable<?>) e).getReference();
                CtExpression<?> rhs = ((CtLocalVariable<?>) e).getAssignment();
                if (rhs == null || rhs instanceof CtInvocation) return false;
                return TypeUtils.isBoxingType(rhs.getType()) && TypeUtils.isPrimitive(lhs.getType());
              })
              .list();

    logger.debug("BOXING TEST");
    for (CtElement call: boxingStmts) {
      printSelectedEle(call);
    }

    List<CtElement> fieldReadStmt = method.filterChildren(new TypeFilter<>(CtFieldRead.class))
          .select((CtFieldRead<?> fr) -> !(fr.getParent() instanceof CtInvocation || fr.getParent() instanceof CtAnnotation))
          .select((CtFieldRead<?> fr) -> {
              List<CtElement> tmpEles = fr.getDirectChildren();
              return tmpEles.stream().filter(e -> e instanceof CtThisAccess).count() == 0;
            })
          .list();  

    logger.debug("FIELD READ STMT: " + methodKey);

    for (CtElement call: fieldReadStmt) {
      printSelectedEle(call);
    }

    mayNPEStmts.addAll(fieldReadStmt);
    mayNPEStmts.addAll(boxingStmts);


    // IF there exist no field access but the method call...
    // if (mayNPEStmts.isEmpty()) {
    //   methodCall = method.filterChildren(new TypeFilter<>(CtInvocation.class))
    //     .select((CtInvocation<?> call) -> {
    //         List<CtElement> tmpEles = call.getDirectChildren();
    //         return (tmpEles.get(0) instanceof CtThisAccess);
    //       })
    //     .list();      
    // }

    int complexity = methodPathMap.get(methodKey).getComplexity();

    if (complexity > 10) {
      methodPathMap.get(methodKey).setNPEPath(true);
      totalPaths += mayNPEStmts.size();

      methodPathMap.get(methodKey).setNPEStmt(mayNPEStmts);

      donePathAnalysis.add(methodKey);
      doneReturnAnalysis.add(methodKey);
      // methodPathMap.put(methodKey, new MethodPath());s

      return;
    }

    CtBlock<?> block = method.getBody();
    List<CtElement> initialList = new ArrayList<>();

    initialList.add(CodeFactory.createLiteral(null));


    if (mayNPEStmts != null) {

      logger.debug("TARGET STMTS: " +methodKey);
      logger.debug(mayNPEStmts.toString());


      for (CtElement call: mayNPEStmts) {

        CtExpression<?> targetExp = null;
        if (call instanceof CtInvocation) targetExp = ((CtInvocation<?>) call).getTarget();
        else if (call instanceof CtLocalVariable) targetExp =  ((CtLocalVariable<?>) call).getAssignment();
        else targetExp = ((CtFieldRead<?>) call).getTarget();

        logger.debug("TARGET EXP OF : " + call.toString());
        // logger.debug(targetExp.toString());
        if (targetExp == null) continue;

        logger.debug("TARGET EXP OF not NULL : " + call.toString());
        logger.debug(targetExp.toString());
        // printSelectedEle(targetExp);

        // entry node for the method
        CtElement startPoint = block.getDirectChildren().get(0);
        startPosition = startPoint.getPosition();

        // List<CtElement> ttt = call.getPath().evaluateOn(startPoint);

        // logger.debug("PATH INFORMATioN");
        // for (CtElement np : ttt) {
        //   printSelectedEle(np);
        // }
        PathBuilder.getInstance().setConstruct(false);

        PathBuilder.getInstance().setStartPosition(startPosition);
        PathBuilder.getInstance().calculatePath(methodKey, block, startPoint, targetExp, initialList);

        
        Set<List<CtElement>> pathResult = PathBuilder.getInstance().getPathList();
        // int pathNum = PathBuilder.getInstance().getPathNum();

        methodPathMap.get(methodKey).setMayNullPath(pathResult);
        
        // pathFromStart(methodKey, block, startPoint, targetExp, initialList, 1);
      }
    }

    logger.debug("PATH RESULTS: " + methodKey);
    logger.debug(methodPathMap.get(methodKey).getMayNullPath().toString());

    
  }

  private void makePathInfo(String methodKey) {
    
    logger.debug("COLLECT RETURN PATHS");
    // checkMethodReturn(methodKey);
    // logger.debug("COLLECT ALL NULLABLE PATHS");
    // getPathToNullable(methodKey);   // get All paths to nullable statment - path information is stored in methodPathMap...getMayNullPath()

    methodReturns(methodKey);

    methodPaths(methodKey);

    // get All paths to return statment - path information is stored in methodPathMap...getReturnPath()
    // If any of the return path in the given method returns null, we do not collect the paths of that method.

    logger.debug("METHOD PATH MAP KEY CHECK");
    for (String ss : methodPathMap.keySet()) {
      logger.debug(ss);
    }logger.debug("METHOD PATH MAP KEY CHECK DONE");

  }

  private void setReturnNull(String methodKey, boolean b) {
      methodPathMap.get(methodKey).setReturnNull(b);
  }

  private void setHasNPEPath(String methodKey, boolean b) {
      methodPathMap.get(methodKey).setNPEPath(b);
  }

  public void analyze(Set<String> methodSet) {
    
    logger.debug("TARGET METHODKEYS");
    for (String methodKey : methodSet) {
      logger.debug(methodKey);
    }

    targetMethodSet = new HashSet<>(methodSet);


    for (String methodKey : methodSet) {
      // logger.debug("STARTS PATH ANLAYSIS: " + methodKey);
      CtMethod<?> tmp = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
      typeKey = tmp.getTopLevelType().getQualifiedName();

      if (targetClass.equals("")) targetClass = typeKey;

      NullableFieldAnalyzer.getInstance().checkConstruct(typeKey);

      // logger.debug("CLASS FIELDS");
      // for (CtField<?> tt : ((CtClass<?>)tmp.getParent()).getFields()) {
      //   logger.debug(tt.toString());

      // }

      // logger.debug("NULLABLE FIELDS");
      // for (CtField<?> tt : NullableFieldAnalyzer.getInstance().getNullableFields(typeKey)) {
      //   logger.debug(tt.toString());
      // }

      // logger.debug("NON NULLABLE FIELDS");
      // for (CtField<?> tt : NullableFieldAnalyzer.getInstance().getCheckingFieldInfo(typeKey)) {
      //   logger.debug(tt.toString());
      // }

      if (!methodPathMap.containsKey(methodKey)) methodPathMap.put(methodKey, new MethodPath());

      ComplexityAnalyzer.getInstance().analyze(methodKey);
      logger.debug("COMPLEXITY SCORE: " + methodKey);
      logger.debug(Integer.toString(ComplexityAnalyzer.getInstance().getScore(methodKey)));

      totalComplexity += ComplexityAnalyzer.getInstance().getScore(methodKey);
      
      makePathInfo(methodKey);
      totalPaths += methodPathMap.get(methodKey).getNumPath();

      
      
      // if (ComplexityAnalyzer.getInstance().getScore(methodKey) < 10) {
      //   makePathInfo(methodKey);

      //   totalPaths += methodPathMap.get(methodKey).getNumPath();
      // } 
      
      // else {
      //   donePathAnalysis.add(methodKey);
      //   doneReturnAnalysis.add(methodKey);



      //   methodPathMap.put(methodKey, new MethodPath());

      //   totalPaths += methodPathMap.get(methodKey).getNumPath();

      // }
    }

    float elapsedTime = Timer.GLOBAL_TIMER.getElapsedTime();
    logger.info("* Make path info finished: {}s", elapsedTime);

    for (String methodKey : methodSet) {
      analysisReturnPath(methodKey);

      analysisNullablePath(methodKey);   


      analysisNPEMethod(methodKey);
      setFieldAccess(methodKey, 0);
      setFieldWrite(methodKey, 0);

      calculateScore(methodKey);

      // logger.debug("RESULTS OF METHOD: " + methodKey);
      // if (methodPathMap.get(methodKey) != null) {
      //   logger.debug("MAY RETURN NULL");
      //   for (List<CtElement> eleList : methodPathMap.get(methodKey).getReturnPath()) {
      //     logger.debug(eleList.toString());
      //   }

      //   logger.debug("# of ALL PATHS: " + Integer.toString(methodPathMap.get(methodKey).getNumPath()));
      //   logger.debug("# of NON NULL PATHS: " + Integer.toString(methodPathMap.get(methodKey).getNonNullPathNum()));
      //   for (List<CtElement> eleList : methodPathMap.get(methodKey).getNonNullPaths()) {
      //     logger.debug(eleList.toString());
      //   }
      //   logger.debug("COLLECTED NULLABLE PATH: " + Integer.toString(methodPathMap.get(methodKey).getNumPath()));
      //   for (List<CtElement> eleList : methodPathMap.get(methodKey).getMayNullPath()) {
      //     logger.debug(eleList.toString());
      //   }
      //   logger.debug("DONE");
      // }
    }
    
    setImpureMethod(methodSet);

    elapsedTime = Timer.GLOBAL_TIMER.getElapsedTime();
    logger.info("* Nullablility check finished: {}s", elapsedTime);

    for (String methodKey : methodSet) {
      logger.debug("SCORE OF : " + methodKey);
      logger.debug(Float.toString(methodPathMap.get(methodKey).getScore()));
      logger.debug(Float.toString(((float)methodPathMap.get(methodKey).getMayNullPath().size())/((float)totalPaths)));
      if (totalComplexity != 0)
        logger.debug(Float.toString(((float) ComplexityAnalyzer.getInstance().getScore(methodKey)) / ((float)totalComplexity)));
      logger.debug("---------------------------------------------------");
    }

  }

  private void printSelectedEle (CtElement element) {
      //Find the level in the Syntax Tree of the element
      int n = 0;
      CtElement parent = element.getParent();
      while (parent != null) {
          n++;
          parent = parent.getParent();
      }

      // Print the element
      try {
          String s = "";
          if (n > 0) s = String.format("%0" + n + "d", 0).replace("0","-");
          logger.debug(s + ", " + element.getClass().getSimpleName() + ", " + element.toString());
      } catch (NullPointerException ex) {
          logger.error("Unknown Element");
      }
  }
  private void printAllEleFromClass (CtElement targetClass) {
    //Find the level in the Syntax Tree of the element
    int n = 0;
    for (CtElement element: targetClass.getElements(null)) {
        n = 0;
        CtElement parent = element.getParent();
        while (parent != null) {
            n++;
            parent = parent.getParent();

        }

        // Print the element
        try {
            String s = "";
            if (n > 0) s = String.format("%0" + n + "d", 0).replace("0","-");
            logger.debug(s + ", " + element.getClass().getSimpleName() + ", " + element.toString());
        } catch (NullPointerException ex) {
            logger.error("Unknown Element");
        }
    }
}

}
