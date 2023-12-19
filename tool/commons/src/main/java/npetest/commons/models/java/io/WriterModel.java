package npetest.commons.models.java.io;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.Writer;
import java.io.StringWriter;
import java.util.stream.Collectors;

public class WriterModel extends ModeledType<Writer> {

  @SuppressWarnings("unchecked")
  public WriterModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<Writer>) ctTypeReference;
    this.ctType = (CtType<StringWriter>) CtModelExt.INSTANCE.getShadowType(StringWriter.class);
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    CtClass<?> ctClass = (CtClass<?>) this.ctType;
    constructor = ctClass.getConstructors().stream()
            .filter(c -> c.getSignature().equals("java.io.StringWriter()"))
            .collect(Collectors.toList()).get(0);
    return constructor;
  }
}
