package npetest.commons.models.java.net;

import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.net.InetAddress;
import java.util.stream.Collectors;

public class InetAddressModel extends ModeledType<InetAddress> {
  @SuppressWarnings("unchecked")
  public InetAddressModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<InetAddress>) ctTypeReference;
    this.ctType = (CtClass<InetAddress>) ctTypeReference.getTypeDeclaration();
  }

  @Override
  public CtMethod<?> getFactoryMethod() {
    if (factoryMethod != null) {
      return factoryMethod;
    }
    factoryMethod = ctType.getMethodsByName("getByName").stream()
            .filter(m -> m.getSignature().equals("getByName(java.lang.String)"))
            .collect(Collectors.toList()).get(0);
    return factoryMethod;
  }
}
