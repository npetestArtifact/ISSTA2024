package npetest.commons.models.java.lang;

import npetest.commons.models.ModeledType;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.reference.CtTypeReference;

public class ObjectModel extends ModeledType<Object> {
  @SuppressWarnings("unchecked")
  public ObjectModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = ctTypeReference;
    this.ctType = TypeUtils.objectType().getTypeDeclaration();
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    constructor = getDefaultConstructor();
    return constructor;
  }
}
