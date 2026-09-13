package org.pcp.exactas.set;

import java.util.concurrent.locks.ReentrantLock;

public class CoarseListSet implements ConcurrentIntSet {
    private final ReentrantLock lock = new ReentrantLock();
    private final Node head = new Node(Long.MIN_VALUE);
    private final Node tail = new Node(Long.MAX_VALUE);

    public CoarseListSet() { head.next = tail; }

    @Override public boolean add(int value) {
        lock.lock();
        Node pred = head;
        Node curr = head.next;
        while (curr.key < value) {
            pred = curr;
            curr = curr.next;
        }
        if (curr.key == value) {
            lock.unlock();
            return false;
        }
        pred.next = new Node(value, curr);
        lock.unlock();
        return true;
    }

    @Override public boolean remove(int value) {
        lock.lock();
        Node pred = head;
        Node curr = head.next;
        while (curr.key < value) {
            pred = curr;
            curr = curr.next;
        }
        if (curr.key != value) {
            lock.unlock();
            return false;
        }
        pred.next = curr.next;
        lock.unlock();
        return true;
        
    }

    @Override public boolean contains(int value) {
        Node curr = head.next;
        while (curr.key < value) {
            curr = curr.next;
        }
        return curr.key == value;
    }
        

    @Override public boolean isImplemented() {
        return true;
    }

    private static final class Node {
        final long key;
        Node next;
        Node(long key) { this(key, null); }
        Node(long key, Node next) { this.key=key; this.next=next; } }
}
