package npetest.synthesizer.generators.stoppers;

import java.util.LinkedList;

class MonitoringQueue<E> extends LinkedList<E> {
  private final int capacity;

  public MonitoringQueue(int capacity) {
    this.capacity = capacity;
  }

  @Override
  public boolean add(E e) {
    super.add(e);
    if (size() > capacity) {
      super.remove();
    }
    return true;
  }

  public int getCapacity() {
    return capacity;
  }
}
