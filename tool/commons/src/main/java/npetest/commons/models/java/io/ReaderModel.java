package npetest.commons.models.java.io;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.Reader;
import java.io.StringReader;
import java.util.stream.Collectors;

public class ReaderModel extends ModeledType<Reader> {

  @SuppressWarnings("unchecked")
  public ReaderModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<Reader>) ctTypeReference;
    this.ctType = (CtType<StringReader>) CtModelExt.INSTANCE.getShadowType(StringReader.class);
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    CtClass<?> ctClass = (CtClass<?>) this.ctType;
    constructor = ctClass.getConstructors().stream()
            .filter(c -> c.getSignature().equals("java.io.StringReader(java.lang.String)"))
            .collect(Collectors.toList()).get(0);
    return constructor;
  }
}
