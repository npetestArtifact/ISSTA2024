package npetest.commons.models.java.io;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.stream.Collectors;

public class FileModel extends ModeledType<File> {
  @SuppressWarnings("unchecked")
  public FileModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<File>) ctTypeReference;
    this.ctType = (CtType<File>) ctTypeReference.getTypeDeclaration();
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    CtClass<?> fileType = (CtClass<?>) CtModelExt.INSTANCE.getShadowType(File.class);
    constructor = fileType.getConstructors().stream()
            .filter(c -> c.getSignature().equals("java.io.File(java.lang.String)")).collect(Collectors.toList()).get(0);
    return constructor;
  }
}
