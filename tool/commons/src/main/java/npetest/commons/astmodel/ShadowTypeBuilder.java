package npetest.commons.astmodel;

import spoon.SpoonException;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class ShadowTypeBuilder {
  private ShadowTypeBuilder() {
  }

  static CtType<?> createShadowCtType(String name) {
    /* Skip anonymous shadow class */
    if (name.matches(".*\\$\\d+")) {
      return null;
    }

    Class<?> shadowType;
    try {
      shadowType = Class.forName(name, true, CtModelExt.INSTANCE.getAuxiliaryLoader());
    } catch (ClassNotFoundException | IncompatibleClassChangeError |
             NoClassDefFoundError | ExceptionInInitializerError |
             UnsatisfiedLinkError | VerifyError e) {
      return null;
    }
    return createShadowType(shadowType);
  }

  static CtType<?> createShadowType(Class<?> shadowType) {
    CtTypeReference<?> shadowCtTypeReference;
    try {
      shadowCtTypeReference = CtModelExt.INSTANCE.getFactory().createCtTypeReference(shadowType);
      return shadowCtTypeReference == null ? null : shadowCtTypeReference.getTypeDeclaration();
    } catch (NoClassDefFoundError | IncompatibleClassChangeError |
             TypeNotPresentException | NullPointerException |
             SpoonException e) {
      return null;
    }
  }
}