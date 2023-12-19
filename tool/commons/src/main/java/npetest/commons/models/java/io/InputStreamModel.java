package npetest.commons.models.java.io;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.stream.Collectors;

public class InputStreamModel extends ModeledType<InputStream> {
  @SuppressWarnings("unchecked")
  public InputStreamModel(CtTypeReference<?> typeReference) {
    this.ctTypeReference = (CtTypeReference<InputStream>) typeReference;
    this.ctType = (CtType<ByteArrayInputStream>) CtModelExt.INSTANCE.getShadowType(ByteArrayInputStream.class);
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    CtClass<?> ctClass = (CtClass<?>) ctType;
    constructor = ctClass.getConstructors().stream()
            .filter(c -> c.getSignature().equals("java.io.ByteArrayInputStream(byte[])"))
            .collect(Collectors.toList()).get(0);
    return constructor;
  }
}
