package npetest.analysis.npeanalysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import npetest.analysis.MethodAnalyzer;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;

public class NullableFieldAccessAnalyzer extends MethodAnalyzer {
  private static final Logger logger = LoggerFactory.getLogger(NullableFieldAccessAnalyzer.class);

  private static final NullableFieldAccessAnalyzer instance = new NullableFieldAccessAnalyzer();

  public static NullableFieldAccessAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> nullableFieldAccessCounts = new HashMap<>();

  @Override
  public void analyze(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
    if (method == null || method.getBody() == null || nullableFieldAccessCounts.containsKey(methodKey)) {
      return;
    }

    CtType<?> declaringType = method.getDeclaringType();
    int weight = countNullableFieldAccess(method, declaringType);

    nullableFieldAccessCounts.put(methodKey, weight);
  }

  private int countNullableFieldAccess(CtMethod<?> method, CtType<?> declaringType) {
    /* Check nullable field access */
    int count = 0;
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields(declaringType.getQualifiedName());
    
    if (nullableFields == null) return 0;

    for (CtField<?> nullableField : nullableFields) {
      count += ASTUtils.countVariableAccess(method, nullableField);
    }
    return count;
  }

  public int getScore(String methodKey) {
    return nullableFieldAccessCounts.getOrDefault(methodKey, 0);
  }


  // -----------------------------------------------------------------------------


  // Check whether the methods has any access to nullable fields
  // if yes, return true. 
  // if there is no access to the field, t
  public boolean hasNullableFieldAccess(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
   
    // logger.info("NULLABLE TESTING METHOD" + methodKey);

    if (method == null || method.getBody() == null) {
      return false;
    }

    CtType<?> declaringType = method.getDeclaringType();

    /* Check nullable field access */
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields(declaringType.getQualifiedName());
    
    // logger.info("nullable Fields are not null");

    if (nullableFields == null) return false;

    for (CtField<?> nullableField : nullableFields) {
      // logger.info(nullableField.toString());
      if (!ASTUtils.hasVariableAccess(method, nullableField)) {
        return true;
      }
    }

    return false;
  }

  // Check whether the methods has any access to the given nullable field
  // if yes, return true;x
  public boolean isAccessedInMethod(String methodKey, CtField<?> field) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);
   
    if (method == null || method.getBody() == null) {
      return false;
    }

    return this.isAccessedInMethod(method, field);
  }

  // Check whether the methods has any access to the given nullable field
  // if yes, return true;
  public boolean isAccessedInMethod(CtMethod<?> method, CtField<?> field) {

    if (!ASTUtils.hasVariableAccess(method, field) 
      )
        return true;

    return false;
    
  }

  //----------------------------------------------------------------------
  // Check whether the given method can redefine the given field.
  // if yes, return false
  public boolean hasFieldWrite(String methodKey, CtField<?> field) { 
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    return method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
          .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
          .list()
          .isEmpty();
  }

  public List<CtFieldWrite<?>> getFieldWriteStmt (String methodKey, CtField<?> field) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    List<CtFieldWrite<?>> tmp = method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
          .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
          .list();

    return tmp;
  }

  public Set<CtField<?>> getAccessedFieldsByMethod(String methodKey) {
    CtMethod<?> method = CtModelExt.INSTANCE.getMethodFromKey(methodKey);

    if (method == null || method.getBody() == null) {
      return null;
    }

    CtType<?> declaringType = method.getDeclaringType();

    /* Check nullable field access */
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields(declaringType.getQualifiedName());
    Set<CtField<?>> result = new HashSet<>();

    if (nullableFields == null) return null;

    for (CtField<?> nullableField : nullableFields) {
      if (!ASTUtils.hasVariableAccess(method, nullableField)) {
        result.add(nullableField);
      }
    }

    return result;
  }


}
