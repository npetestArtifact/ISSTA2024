package npetest.commons.spoon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
public class TypeUtilsTest {
  Factory factory;

  @Before
  public void setUp() {
    factory = new Launcher().getFactory();
    TypeUtils.setup(factory);
  }

  @Test
  public void testIsRawType() {
    /* tests for generic type */
    assertTrue(TypeUtils.isRawType(factory.createCtTypeReference(List.class)));
    assertFalse(TypeUtils.isRawType(factory.createCtTypeReference(List.class).setActualTypeArguments(
            Collections.singletonList(TypeUtils.stringType()))));

    /* tests for array type */
    CtArrayTypeReference<Integer[]> arrayReference = factory.createArrayReference(TypeUtils.integerPrimitive());
    assertFalse(TypeUtils.isRawType(arrayReference));

    /* tests for type without declaration */
    assertFalse(TypeUtils.isRawType(factory.Type().createReference("unknown.Type")));
  }

  @Test
  public void testParameterizedTypeOfRawType() {
    /* tests with java.util.List */
    CtTypeReference<?> rawListType = factory.createCtTypeReference(List.class);
    CtTypeReference<?> parameterizedListType = TypeUtils.parameterizedTypeOfRawType(rawListType);

    assertFalse(TypeUtils.isRawType(parameterizedListType));

    int formalTypeParameterCountOfList = rawListType.getTypeDeclaration().getFormalCtTypeParameters().size();
    assertEquals(formalTypeParameterCountOfList, parameterizedListType.getActualTypeArguments().size());
    for (CtTypeReference<?> actualTypeArgument : parameterizedListType.getActualTypeArguments()) {
      assertEquals(TypeUtils.wildcard(), actualTypeArgument);
      assertEquals("?", actualTypeArgument.getSimpleName());
      assertEquals("?", actualTypeArgument.toString());
    }

    /* tests with java.util.Map */
    CtTypeReference<?> rawMapType = factory.createCtTypeReference(Map.class);
    CtTypeReference<?> parameterizedMapType = TypeUtils.parameterizedTypeOfRawType(rawMapType);

    assertFalse(TypeUtils.isRawType(parameterizedMapType));

    int formalTypeParameterCountOfMap = rawMapType.getTypeDeclaration().getFormalCtTypeParameters().size();
    assertEquals(formalTypeParameterCountOfMap, parameterizedMapType.getActualTypeArguments().size());
    for (CtTypeReference<?> actualTypeArgument : parameterizedMapType.getActualTypeArguments()) {
      assertEquals(TypeUtils.wildcard(), actualTypeArgument);
      assertEquals("?", actualTypeArgument.getSimpleName());
      assertEquals("?", actualTypeArgument.toString());
    }
  }

  @Test
  public void testIsDeclarable() {
    List<CtTypeReference<?>> primitiveTypes = Arrays.asList(
            /* primitive types */
            TypeUtils.booleanPrimitive(), TypeUtils.bytePrimitive(),
            TypeUtils.shortPrimitive(), TypeUtils.integerPrimitive(),
            TypeUtils.longPrimitive(), TypeUtils.floatPrimitive(),
            TypeUtils.doublePrimitive(), TypeUtils.charPrimitive(),
            /* boxing types */
            TypeUtils.booleanBoxingType(), TypeUtils.byteBoxingType(),
            TypeUtils.shortBoxingType(), TypeUtils.integerBoxingType(),
            TypeUtils.longBoxingType(), TypeUtils.floatBoxingType(),
            TypeUtils.doubleBoxingType(), TypeUtils.charBoxingType(),
            /* string type */
            TypeUtils.stringType()
    );

    for (CtTypeReference<?> primitiveType : primitiveTypes) {
      assertTrue(TypeUsabilityChecker.isDeclarable(primitiveType, false));
    }

    /* raw type */
    CtTypeReference<Object> listRawType = factory.createCtTypeReference(List.class);
    CtTypeReference<Object> setRawType = factory.createCtTypeReference(Set.class);
    CtTypeReference<Object> mapRawType = factory.createCtTypeReference(Map.class);
    assertFalse(TypeUsabilityChecker.isDeclarable(listRawType, false));
    assertFalse(TypeUsabilityChecker.isDeclarable(setRawType, false));
    assertFalse(TypeUsabilityChecker.isDeclarable(mapRawType, false));

    /* wildcard-parameterized type */
    CtTypeReference<?> listTypeWithWildcard = TypeUtils.parameterizedTypeOfRawType(listRawType);
    CtTypeReference<?> setTypeWithWildcard = TypeUtils.parameterizedTypeOfRawType(setRawType);
    CtTypeReference<?> mapTypeWithWildcard = TypeUtils.parameterizedTypeOfRawType(mapRawType);
    assertFalse(TypeUsabilityChecker.isDeclarable(listTypeWithWildcard, false));
    assertFalse(TypeUsabilityChecker.isDeclarable(setTypeWithWildcard, false));
    assertFalse(TypeUsabilityChecker.isDeclarable(mapTypeWithWildcard, false));

    /* bounded-wildcard-parameterized type */
    CtTypeReference<?> listTypeWithBoundedWildcard = listTypeWithWildcard.clone();
    CtWildcardReference wildcard = (CtWildcardReference) listTypeWithBoundedWildcard.getActualTypeArguments().get(0);
    wildcard.setBoundingType(TypeUtils.stringType());
    assertTrue(TypeUsabilityChecker.isDeclarable(listTypeWithBoundedWildcard, false));

    CtTypeReference<?> mapTypeWithOneBoundedWildcard = mapTypeWithWildcard.clone();
    CtWildcardReference wildcard1 = (CtWildcardReference) mapTypeWithOneBoundedWildcard.getActualTypeArguments().get(0);
    wildcard1.setBoundingType(TypeUtils.stringType());
    assertFalse(TypeUsabilityChecker.isDeclarable(mapTypeWithOneBoundedWildcard, false));

    CtTypeReference<?> mapTypeWithTwoBoundedWildcards = mapTypeWithOneBoundedWildcard.clone();
    CtWildcardReference wildcard2 = (CtWildcardReference) mapTypeWithTwoBoundedWildcards.getActualTypeArguments().get(1);
    wildcard2.setBoundingType(TypeUtils.stringType());
    assertTrue(TypeUsabilityChecker.isDeclarable(mapTypeWithTwoBoundedWildcards, false));
  }
}