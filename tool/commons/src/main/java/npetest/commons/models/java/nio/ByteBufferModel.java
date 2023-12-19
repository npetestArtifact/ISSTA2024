package npetest.commons.models.java.nio;

import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.nio.ByteBuffer;

public class ByteBufferModel extends ModeledType<ByteBuffer> {
  @SuppressWarnings("unchecked")
  public ByteBufferModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<ByteBuffer>) ctTypeReference;
    this.ctType = (CtClass<ByteBuffer>) ctTypeReference.getTypeDeclaration();
  }

  @Override
  public CtMethod<?> getFactoryMethod() {
    if (factoryMethod != null) {
      return factoryMethod;
    }
    factoryMethod = ctType.getMethodsByName("allocate").get(0);
    return factoryMethod;
  }
}
