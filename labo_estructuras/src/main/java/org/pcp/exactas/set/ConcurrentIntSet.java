package org.pcp.exactas.set;

public interface ConcurrentIntSet {
    boolean add(int value);

    boolean remove(int value);

    boolean contains(int value);

    default boolean isImplemented() {
        return false;
    }
}
