package npetest.synthesizer.search.mut;

import npetest.commons.keys.ExecutableKey;

import java.util.HashSet;
import java.util.Set;

public abstract class MUTSelector {
  protected final Set<ExecutableKey> muts = new HashSet<>();

  public abstract ExecutableKey choose();

  public Set<ExecutableKey> getMUT() {
    return muts;
  }

  public void setup(Set<ExecutableKey> muts) {
    this.muts.addAll(muts);
  }

  public Set<ExecutableKey> getMUTs() {
    return muts;
  }
}
