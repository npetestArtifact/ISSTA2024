package npetest.commons.models;

import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModeledTypes {
  static final Map<String, List<String>> modeledTypesMap = new HashMap<String, List<String>>() {{
    put("java.nio.ByteBuffer", Arrays.asList("java.nio.ByteBuffer"));

    put("java.net.InetAddress", Arrays.asList("java.net.InetAddress"));

    put("java.io.File", Arrays.asList("java.io.File"));
    put("java.io.InputStream", Arrays.asList("java.io.ByteArrayInputStream"));
    put("java.io.Reader", Arrays.asList("java.io.StringReader"));
    put("java.io.Writer", Arrays.asList("java.io.StringWriter"));
    put("java.lang.Object", Arrays.asList("java.lang.Object"));

    put("java.util.Collection",
            Arrays.asList("java.util.ArrayList", "java.util.HashMap", "java.util.HashSet"));
    put("java.util.List", Arrays.asList("java.util.ArrayList"));
    put("java.util.Map", Arrays.asList("java.util.HashMap"));
    put("java.util.Set", Arrays.asList("java.util.HashSet"));

    put("java.util.Properties", Arrays.asList("java.util.Properties"));
  }};


  public static boolean contains(CtTypeReference<?> typeReference) {
    return modeledTypesMap.values().stream().anyMatch(
            typeList -> typeList.contains(typeReference.getQualifiedName()))
            || modeledTypesMap.keySet().stream().anyMatch(
            typeList -> typeList.contains(typeReference.getQualifiedName()));
  }

  public static boolean hasModeledSubtype(CtTypeReference<?> typeReference) {
    return modeledTypesMap.containsKey(typeReference.getQualifiedName());
  }
}
