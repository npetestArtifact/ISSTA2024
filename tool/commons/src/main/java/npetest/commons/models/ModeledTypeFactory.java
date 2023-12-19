package npetest.commons.models;

import npetest.commons.models.java.io.FileModel;
import npetest.commons.models.java.io.InputStreamModel;
import npetest.commons.models.java.io.ReaderModel;
import npetest.commons.models.java.io.WriterModel;
import npetest.commons.models.java.lang.ObjectModel;
import npetest.commons.models.java.net.InetAddressModel;
import npetest.commons.models.java.nio.ByteBufferModel;
import npetest.commons.models.java.util.ListModel;
import npetest.commons.models.java.util.MapModel;
import npetest.commons.models.java.util.PropertiesModel;
import npetest.commons.models.java.util.SetModel;
import spoon.reflect.reference.CtTypeReference;

public class ModeledTypeFactory {
  public static ModeledType<?> create(CtTypeReference<?> typeReference) {
    String qualifiedName = typeReference.getTypeErasure().getQualifiedName();
    switch (qualifiedName) {
      case "java.nio.ByteBuffer":
        return new ByteBufferModel(typeReference);

      case "java.net.InetAddress":
        return new InetAddressModel(typeReference);

      case "java.io.File":
        return new FileModel(typeReference);

      case "java.io.InputStream":
      case "java.io.ByteArrayInputStream":
        return new InputStreamModel(typeReference);

      case "java.io.Reader":
        return new ReaderModel(typeReference);

      case "java.io.Writer":
        return new WriterModel(typeReference);

      case "java.lang.Object":
        return new ObjectModel(typeReference);

      case "java.util.Collection":
      case "java.util.List":
      case "java.util.ArrayList":
        return new ListModel(typeReference);

      case "java.util.Map":
      case "java.util.HashMap":
        return new MapModel(typeReference);

      case "java.util.Set":
      case "java.util.HashSet":
        return new SetModel(typeReference);

      case "java.util.Properties":
        return new PropertiesModel(typeReference);

      default:
        throw new IllegalStateException(qualifiedName);
    }
  }
}
