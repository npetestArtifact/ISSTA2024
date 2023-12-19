package npetest.analysis.npeanalysis;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.TypeKey;
import npetest.commons.logger.LoggingConfiguration;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ImpurityAnalyzer extends MethodAnalyzer {

  private boolean analyzed = false;
  private static final Logger logger = LoggerFactory.getLogger(ImpurityAnalyzer.class);

  private String typeKey;
  
  private Set<CtField<?>> classFields;
  private Set<CtField<?>> nonNullFields;      // Fields that are not initialized to null in constructor

  private Set<String> worklist = new HashSet<>();
  // Set of all methods in the target class
  private Set<String> methodsInClass = new HashSet<>();

  private final Map<String, HashSet<CtField<?>>> modifiedFields = new HashMap<>();

  // 
  private final Map<CtField<?>, HashSet<String>> fieldToMethods = new HashMap<>();

  private final Map<String, Integer> impurityScores = new HashMap<>();

  private final HashSet<String> impureMethods = new HashSet<>();

  // private final Map<CtTypeReference<?>, List<CtMethod<?>>> relatedMethods = new HashMap<>();

  // set of impure methods which redefine any fields used in the current method
  // To guide the method selection during mutation process.
  private final Map<String, List<CtMethod<?>>> relatedMethods = new HashMap<>();

  // set of methods returning null (possible)
  private final Map<String, Boolean> methodReturnNull = new HashMap<>();
  
  private static final ImpurityAnalyzer instance = new ImpurityAnalyzer();

  // Set of NPE-likely methods
  private final HashSet<String> privateNPEMethods = new HashSet<>();
  private final HashSet<String> publicNPEMethods = new HashSet<>(); //MUT
  private final HashSet<String> protectedNPEMethods = new HashSet<>(); //MUT

  public static ImpurityAnalyzer getInstance() {
    return instance;
  }

  public Set<CtField<?>> getFields() {
    return this.classFields;
  }

  private void analyzeImpureMethod() {
    for (CtField<?> field : this.classFields) {
      HashSet<String> tmpSet = new HashSet<>();

      tmpSet = methodsInClass.stream().filter(
        method -> NullableFieldAccessAnalyzer.getInstance().isAccessedInMethod(method, field) ||
        NullableFieldAccessAnalyzer.getInstance().hasFieldWrite(method, field)
      ).collect(Collectors.toCollection(HashSet::new));

      fieldToMethods.put(field, tmpSet);
    }
  }

  private void runAnalyzer() {
    analyzeImpureMethod();

  }

  public float getScore(String methodKey) {
    logger.debug("SCORE OF THE SEED");
    return PathAnalyzer.getInstance().getScore(methodKey);
  }

  private boolean hasImpureMethod(CtMethod<?> method, CtField<?> field) {
    // if (fieldToMethods.get(field).contains(method))
    // for (CtMethod<?> method : fieldToMethods.get(field)) 

    List<CtInvocation<?>> invocationCalls = method.filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> invocation) -> invocation.getExecutable() != null &&
                    invocation.getExecutable().getExecutableDeclaration() != null &&
                    invocation.getExecutable().getExecutableDeclaration() instanceof CtShadowable)
            .list();

    return false;
  }

  private void analyzeField() {
    HashSet<String> tmpSet = new HashSet<>(methodsInClass);
    tmpSet.retainAll(worklist);
    for (String methodKey : tmpSet) {
      boolean allDone = false;
      for (CtField<?> field : nonNullFields) {

        if (NullableFieldAccessAnalyzer.getInstance().hasFieldWrite(methodKey, field)) {
                    
        }
      }

      if (allDone) worklist.remove(methodKey);
    }

    if (!worklist.isEmpty()) analyzeField();
  }

  private boolean checkFieldWriteNull (CtFieldWrite fw) {
    CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
    CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);

    // Check whether there exists any field write which directly assignes null to any field.
    if (rhs.getValueByRole(CtRole.TYPE) == null || TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE)))
      return true;

    return false;
  }

  /**
   * @param methodKey
   */
  private void impurityAnalysis(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    List<CtFieldWrite<?>> tmpFieldWrite = method.filterChildren(new TypeFilter(CtFieldWrite.class)).list();

    List<CtFieldWrite<?>> nullToField = method.filterChildren(new TypeFilter(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> !TypeUtils.isPrimitive(fw.getType()))
              .select((CtFieldWrite<?> fw) -> checkFieldWriteNull(fw))
          .list();

    if (!tmpFieldWrite.isEmpty()) {
      for (CtFieldWrite<?> fw : tmpFieldWrite) {
        CtField<?> tmpField = fw.getVariable().getFieldDeclaration();
        HashSet<String> tmpSet = fieldToMethods.get(tmpField);

        if (tmpSet == null) {
          tmpSet = new HashSet<>();
        }
        tmpSet.add(methodKey);
        fieldToMethods.put(tmpField, tmpSet);
      }
    }
    // logger.debug("CHECK NULL TOFIELD EMPTY??");

    if (!nullToField.isEmpty()) {
        // logger.debug("DELTE NON NULL FIELDS");
      for (CtFieldWrite<?> fw : nullToField) {
        CtField<?> tmpField = fw.getVariable().getFieldDeclaration();

        logger.debug(tmpField.toString());

        nonNullFields.remove(tmpField);      }
        // logger.debug("DELTE NON NULL FIELDS ENDS");
    }
  }

  // check whether the given field can be redefined in the given method
  // 1) if it is newly initial...
  // 2) Used as lVar in any assignment
  // 3) Used as a receiver object
  private boolean checkDefOfField(CtMethod<?> method, CtField<?> field) {
    //     method.filterChildren(new TypeFilter(CtField.class))
    // .select((CtField<?> f) -> f.getParent() instanceof CtAssignment && ) 


    return false;
  }

  public boolean isAnalyzed() {
    return this.analyzed;
  }

  public List<CtMethod<?>> getRelatedMethods(String methodKey) {
    return this.relatedMethods.get(methodKey);
  }

  private void setMethodSet() {

    for (String methodKey : methodsInClass) {
      // logger.debug ("BEFORE ENTERING SETMETHOD SET: " + m);
      // methodWorklist.add(m);
      // setMethodSet(m);

      // logger.debug("START ANALYZE");
      // Analyze the given method
      NullLiteralAnalyzer.getInstance().analyze(methodKey);
      NullableFieldAccessAnalyzer.getInstance().analyze(methodKey);
      NullableParameterAccessAnalyzer.getInstance().analyze(methodKey);
    }
    


    for (String methodKey : methodsInClass) {
      impurityAnalysis(methodKey);

      CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

      if (impureMethods.contains(methodKey) || isNPEMethod(methodKey)) {
        if (method.isPublic()) publicNPEMethods.add(methodKey);
        else if (method.isProtected()) protectedNPEMethods.add(methodKey);
        else if (method.isPrivate()) privateNPEMethods.add(methodKey);
      }
    }
    
  }


  // Check whether the given method is NPE-related... (if yes, return true)
  // 1) the method has any local variables that are nullable
  // 2) the method has any accesses to paramters that are nullable
  private boolean isNPEMethod (String methodKey) {
    // CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    // for (int i = 0; i < method.getParameters().size(); i++) {
    //   CtParameter<?> parameter = method.getParameters().get(i);
    //   printSelectedEle(parameter);
    //   // if (!TypeUtils.isPrimitive(parameter.getType().getTypeDeclaration())) {
    //   //   if (!ASTUtils.hasVariableAccess(method, parameter)) return true;
    //   // }
    // }

    return (NullLiteralAnalyzer.getInstance().hasNullableLocalVariable(methodKey) ||
            NullableParameterAccessAnalyzer.getInstance().hasNullableParamterAccess(methodKey) ||
            NullableFieldAccessAnalyzer.getInstance().hasNullableFieldAccess(methodKey)
            );
  }

  // get a set of all methods in the target class
  private void setMethodsInClass(CtMethod<?> method) {

    CtElement targetClass = method.getParent();

    List<CtMethod<?>> filteredMethods = targetClass.filterChildren(new TypeFilter<>(CtMethod.class))
            .select((CtMethod<?> m) -> m.getDeclaringType().getQualifiedName().equals(typeKey)).list();
    
    for (CtMethod<?> m : filteredMethods) {
      String tmpKey = typeKey + "#" + m.getSignature();

      methodsInClass.add(tmpKey);
    }
  }

  private void setInitialClassFields(CtMethod<?> method) {
    CtType<?> declaringType = method.getDeclaringType();

    this.classFields = NullableFieldAnalyzer.getInstance().getNullableFields(declaringType.getQualifiedName());
    
    this.nonNullFields = NullableFieldAnalyzer.getInstance().getCheckingFieldInfo(this.typeKey);
  }

  // initial information of the given class
  private void initialize(String methodKey) {

    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    
    setMethodsInClass(method);
    setInitialClassFields(method);
  }

  @Override
  public void analyze(String methodKey) {
    if (methodsInClass.contains(methodKey)) return;
    // if (this.analyzed) return;

    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    
    
    this.typeKey = method.getTopLevelType().getQualifiedName();

    logger.debug("Obtain Private/Public Method Set from CUT (Class Under Test): " + methodKey);
    initialize(methodKey);

    // if (LoggingConfiguration.isLogLevelDebug()) {
    //   logger.debug("Raw INFO");
    //   printAllEleFromClass(method.getParent());
    // }

  }

  public void runPathAnalyzer() {    
    logger.debug("# methos in CLASS: " + Integer.toString(methodsInClass.size()));
    for (String gg : methodsInClass) {
      logger.debug(gg);
    }

    PathAnalyzer.getInstance().analyze(methodsInClass);

    return;
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

}
