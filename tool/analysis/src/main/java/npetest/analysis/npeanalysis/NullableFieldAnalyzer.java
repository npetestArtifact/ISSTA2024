package npetest.analysis.npeanalysis;

import npetest.analysis.ClassAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullableFieldAnalyzer extends ClassAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(NullableFieldAnalyzer.class);
  private static final NullableFieldAnalyzer instance = new NullableFieldAnalyzer();

  public static NullableFieldAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Set<CtField<?>>> nullableFieldsMap = new HashMap<>();

  private final Set<ExecutableKey> nullableFieldGetters = new HashSet<>();

  private final Set<CtField<?>> nonNullableFields = new HashSet<>();

  private final Map<String, Set<CtField<?>>> nullableFieldsToMethod = new HashMap<>();

  private final Map<String, Set<CtField<?>>> unInitializedFieldsMap = new HashMap<>();
// 
//   private final Set<CtField<?>> unInitializedFields = new HashSet<>();

  private final Set<String> doneAnalysis = new HashSet<>();

  public void analyze(String className) {
    CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);

    if (ctType == null || nullableFieldsMap.containsKey(className)) {
      return;
    }

    Set<CtField<?>> nullableFields = findNullableFields(ctType);
    updateNullableFieldGetters(ctType, nullableFields);
    nullableFieldsMap.put(className, nullableFields);
  }

  private Set<CtField<?>> findNullableFields(CtType<?> ctType) {
    Set<CtField<?>> nullableFields = new HashSet<>();
    if (ctType.isClass()) {
      nullableFields.addAll(inferNullableFieldFromConstructors((CtClass<?>) ctType));
    }
    nullableFields.addAll(inferNullableFieldFromNullHandlingCode(ctType));
    return nullableFields;
  }

  private Set<CtField<?>> inferNullableFieldFromNullHandlingCode(CtType<?> ctType) {
    List<CtField<?>> nullableFields = ctType.filterChildren(new TypeFilter<>(CtBinaryOperator.class))
            .select((CtBinaryOperator<?> binOp) -> binOp.getKind().equals(BinaryOperatorKind.EQ)
                    || binOp.getKind().equals(BinaryOperatorKind.NE))
            .select(ASTUtils::isNullCheckingConditionForField)
            .map(ASTUtils::extractFieldAccess)
            .map((CtFieldAccess<?> fa) -> fa.getVariable().getFieldDeclaration())
          .list();
    return new HashSet<>(nullableFields);
  }

  private Set<CtField<?>> inferNullableFieldFromConstructors(CtClass<?> ctClass) {
    Set<CtField<?>> nullableFields = new HashSet<>();
    Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();
    for (CtField<?> field : ctClass.getFields()) {
      if (TypeUtils.isPrimitive(field.getType()) || field.getDefaultExpression() != null) {
        continue;
      }
      boolean isFieldNullable = false;
      for (CtConstructor<?> constructor : constructors) {
        if (isFieldNullable) {
          break;
        }
        isFieldNullable = getInitializedFields(constructor).contains(field);
      }

      if (isFieldNullable) {
        nullableFields.add(field);
      }
    }
    return nullableFields;
  }

  private Set<CtField<?>> getInitializedFields(CtConstructor<?> constructor) {
    List<CtFieldWrite<?>> writtenFields = constructor.filterChildren(new TypeFilter<>(CtFieldWrite.class))
            .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
            .select((CtFieldWrite<?> fw) -> {
              CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
              CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
              return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
            })
            .list();
    return writtenFields.stream()
            .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
            .collect(Collectors.toSet());
  }

  private void updateNullableFieldGetters(CtType<?> ctType, Set<CtField<?>> nullableFields) {
    if (!ctType.isClass()) {
      return;
    }
    CtClass<?> ctClass = (CtClass<?>) ctType;
    for (CtMethod<?> method : ctClass.getMethods()) {
      CtTypeReference<?> returnType = method.getType();
      if (TypeUtils.isVoidPrimitive(returnType)) {
        continue;
      }

      List<CtReturn<?>> returns = method.filterChildren(new TypeFilter<>(CtReturn.class)).list();
      boolean returnNullableField = returns.stream()
              .anyMatch(ret -> ret.getReturnedExpression() instanceof CtFieldAccess<?>
                      && ((CtFieldAccess<?>) ret.getReturnedExpression()).getVariable() != null &&
                      nullableFields.contains(((CtFieldAccess<?>) ret.getReturnedExpression()).getVariable()
                              .getFieldDeclaration()));
      if (returnNullableField) {
        nullableFieldGetters.add(ExecutableKey.of(method));
      }
    }
  }

  public Set<ExecutableKey> getNullableFieldGetters() {
    return nullableFieldGetters;
  }

  public Set<CtField<?>> getNullableFields(String className) {
    if (!nullableFieldsMap.containsKey(className)) 
      analyze(className);
      
    return nullableFieldsMap.get(className);
  }



  // --------------------------------------------------------------------------------------

  // Gather all fields which are initially not null
  // 1) The default value is not null when initialized
  // 2) The value is set to nonnull when calling constructor
  private void constructorCheck(String className) {
    CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);

    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();

      for (CtField<?> field : ctClass.getFields()) {
        if (TypeUtils.isPrimitive(field.getType())) {
          continue;
        }

        for (CtConstructor<?> constructor : constructors) {
          CtElement targetBlk = null;
          List<CtElement> tmpList = constructor.filterChildren(new TypeFilter<>(CtIf.class)).list();
          if (!tmpList.isEmpty()) {

            for (CtElement ifEle : tmpList) {
              targetBlk = ifEle;

              // logger.debug("CONSCTURCTOR CHECK");
              List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                      .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                      .select((CtFieldWrite<?> fw) -> {
                        CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                        CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                        // logger.debug(rhs.toString());
                        return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                      })
                      .list();

              writtenFields.stream()
                      .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                      .collect(Collectors.toSet());

              // logger.debug("CONSTRUCTOR FIELDS CHECK");
              for (CtFieldWrite<?> fw : writtenFields) {
                // logger.debug(fw.getVariable().getFieldDeclaration().toString());
                nonNullableFields.add(fw.getVariable().getFieldDeclaration());
              }

            }

          } else {
            targetBlk = constructor;

            // logger.debug("CONSCTURCTOR CHECK");
            List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                    .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                    .select((CtFieldWrite<?> fw) -> {
                      CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                      CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                      // logger.debug(rhs.toString());
                      return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                    })
                    .list();

            writtenFields.stream()
                    .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                    .collect(Collectors.toSet());

            // logger.debug("CONSTRUCTOR FIELDS CHECK");
            for (CtFieldWrite<?> fw : writtenFields) {
              // logger.debug(fw.getVariable().getFieldDeclaration().toString());
              nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            }     

          }      
        }

        // logger.debug("CONSTRUCTOR DONE");

        if (field.getDefaultExpression() != null) {
          // logger.debug("DEFAULT EXPRESSION IS NOT NULL: " + field.toString());
          // logger.debug(field.getDefaultExpression().toString());
          nonNullableFields.add(field);
        } 
        // else {
        //   logger.debug("FIELD DEFAULTEXPRESSION IS NULL");
        //   logger.debug(field.toString());
        // }

      }
    }
  }

  

  public void checkConstruct(String className) {
    CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);

    if (ctType == null || doneAnalysis.contains(className)) {
      return;
    }

    Set<CtField<?>> unInitializedFields = new HashSet<>();

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();

      Set<List<CtElement>> constructorPaths = new HashSet<>();

      for (CtConstructor<?> constructor : constructors) {

        // logger.debug("CONSCTURCTOR CHECK");
        List<CtFieldWrite<?>> writtenFields = constructor.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                // .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                .select((CtFieldWrite<?> fw) -> !TypeUtils.isPrimitive(fw.getType()))
                .select((CtFieldWrite<?> fw) -> {
                  CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                  CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                  // logger.debug(rhs.toString());
                  return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                })
                .list();

        List<CtElement> initialList = new ArrayList<>();

        initialList.add(CodeFactory.createLiteral(null));

        for (CtFieldWrite<?> fw : writtenFields) {
          CtElement startPoint = constructor.getBody().getDirectChildren().get(0);
          SourcePosition startPosition = startPoint.getPosition();
          String methodKey = className + "#" + constructor.getSignature();
          
          PathBuilder.getInstance().setStartPosition(startPosition);
          PathBuilder.getInstance().setConstruct(true);

          PathBuilder.getInstance().calculatePath(methodKey, constructor.getBody(), startPoint, fw, initialList);

          PathBuilder.getInstance().setConstruct(false);

          Set<List<CtElement>> pathResult = PathBuilder.getInstance().getPathList();
          constructorPaths.addAll(pathResult);
          break;
        }    
      }

      for (CtField<?> field : ctClass.getFields()) {        
        if (TypeUtils.isPrimitive(field.getType())) {
          continue;
        }
        
        boolean pathResult = false;

        // logger.debug("CONST PATH:");
        // for (List<CtElement> path : constructorPaths) {
        //   logger.debug(path.toString());
        // }
        // logger.debug("DONE");
        
        for (List<CtElement> path : constructorPaths) {
          pathResult = false;
          

          for (CtElement p : path) {

            //writtenFields is a set of elements which are not null.
            List<CtFieldWrite<?>> writtenFields = p.filterChildren(new TypeFilter<>(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
              .select((CtFieldWrite<?> fw) -> {

              
                CtConstructor<?> constructor = fw.getParent(CtConstructor.class);
                // String methodKey = className + "#" + constructor.getSignature();
                
                // Set<String> nullableParams = NullLiteralAnalyzer.getInstance().getNullableParams(methodKey);
                
                List<CtParameter<?>> params = constructor.getParameters();
                Set<String> tmp = new HashSet<String>();

                for (CtParameter<?> param : params) {
                  if (!TypeUtils.isPrimitive(param.getType())) {
                    tmp.add(param.getSimpleName());
                  }
                }

                CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);

                // logger.debug("CONSTR CHECKING");
                // logger.debug(rhs.toString());
                // logger.debug(tmp.toString());
                // logger.debug(rhs.toString());
                return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE)) && !(tmp.toString().equals(rhs.toString()));
              })
              .select((CtFieldWrite<?> fw) -> fw.getVariable().toString().equals(field.getSimpleName()))
              // .select((CtFieldWrite<?> fw) -> {
              //   logger.debug("FIELD WRITE TEST");
              //   logger.debug(fw.getVariable().toString());
              //   logger.debug(fw.toString());
              //   logger.debug(field.getSimpleName());
              //   fw.getVariable().toString().equals(field.getSimpleName());
              //   return true;
              // })
              .list();

            // logger.debug("YAYA");
            // logger.debug(writtenFields.toString());

            if (!writtenFields.isEmpty()) {
              pathResult = true;
              break;
            }
                          
          }

          if (!pathResult) break; 
        }

        if (!pathResult && field.getDefaultExpression() == null) {
          unInitializedFields.add(field);
        }
      }

      
    }

    logger.debug("UNINITIALIZE");
    for (CtField<?> tt : unInitializedFields) {
      logger.debug(tt.toString());
    }

    unInitializedFieldsMap.put(className, unInitializedFields);

    doneAnalysis.add(className);
  }

  public Set<CtField<?>> getUnInitFields(String className) {
    return unInitializedFieldsMap.get(className);

    
  }


  // Gather all fields which never become null
  // 1) The initial value is not null
  // 2) There exists no methods which may set the field to null 
  //    - not directly assignes null to the given field
  //    - no external calls to the given field (except certain constructors... new HashSet..)
  private void methodCheck(String className) {
    CtType<?> ctType = CtModelExt.INSTANCE.getCtTypeFromModel(className);

    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtMethod<?>> methods = ctClass.getAllMethods();
      Set<CtField<?>> tmpFields = new HashSet(nonNullableFields);

      for (CtField<?> field : tmpFields) {
        boolean check = false;

        for (CtMethod<?> method : methods) {

          List<CtFieldWrite<?>> writtenFields = method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                  .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                  .select((CtFieldWrite<?> fw) -> {
                    CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                    CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                    return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                  })
                  .list();
          writtenFields.stream()
                  .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                  .collect(Collectors.toSet());
          for (CtFieldWrite<?> fw : writtenFields) {
            nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            
          }           
        }

        if (check) nonNullableFields.remove(field);

      }
    }
  }


  // Return a set of fields which are initially set to non-null.
  // If the class has no method which assignes the fields to null,
  // the fields in "nonNullableFields" never becomes null.
  public Set<CtField<?>> getCheckingFieldInfo(String className) {
    constructorCheck(className);
    methodCheck(className);
      
    return nonNullableFields;
  }

  public boolean isNonNullable(CtElement ele) {

    if (nonNullableFields.contains(ele)) return true;
    else return false;
  }

  public boolean isNonNullable(String varName) {

    if (nonNullableFields.stream().filter(f -> f.getSimpleName().equals(varName)).count() > 0) return true;
    else return false;
  }

  public boolean isNullable(String className, String varName) {
    
    if (unInitializedFieldsMap.get(className).stream().filter(f -> f.getSimpleName().equals(varName)).count() > 0) return true;
    else return false;
  }
}
