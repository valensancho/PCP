package org.pcp.exactas.set;

// La lista enlazada de toda la vida, muy insegura cuando es usada por más de un thread
public class SequentialListSet implements ConcurrentIntSet {
    private final Node head = new Node(Long.MIN_VALUE);
    private final Node tail = new Node(Long.MAX_VALUE);

    public SequentialListSet() {
        head.next = tail;
    }

    @Override
    public boolean add(int value) {
        Node pred = head;
        Node curr = head.next;
        while (curr.key < value) {
            pred = curr;
            curr = curr.next;
        }
        if (curr.key == value) {
            return false;
        }
        pred.next = new Node(value, curr);
        return true;
    }

    @Override
    public boolean remove(int value) {
        Node pred = head;
        Node curr = head.next;
        while (curr.key < value) {
            pred = curr;
            curr = curr.next;
        }
        if (curr.key != value) {
            return false;
        }
        pred.next = curr.next;
        return true;
    }

    @Override
    public boolean contains(int value) {
        Node curr = head.next;
        while (curr.key < value) {
            curr = curr.next;
        }
        return curr.key == value;
    }

    @Override
    public boolean isImplemented() { return true; }

    private static final class Node {
        final long key;
        Node next;

        Node(long key) { this(key, null); }
        Node(long key, Node next) { this.key = key; this.next = next; }
    }
}
