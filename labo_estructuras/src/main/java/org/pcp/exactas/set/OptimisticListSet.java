package org.pcp.exactas.set;

import java.util.concurrent.locks.ReentrantLock;

public class OptimisticListSet implements ConcurrentIntSet {
    private final Node head = new Node(Long.MIN_VALUE);
    private final Node tail = new Node(Long.MAX_VALUE);

    public OptimisticListSet() { head.next = tail; }

    @Override public boolean add(int value) {
        while(true){
            Node pred = head, curr = head.next;
            while (curr.key < value) {
                pred = curr;
                curr = curr.next;
            }
            if (curr.key == value) {
                return false;
            }
            pred.lock.lock();
            curr.lock.lock();
            try {
                if (edgeExists(pred, curr)) {
                    pred.next = new Node(value, curr);
                    return true;
                }
            }
            finally {
                pred.lock.unlock();
                curr.lock.unlock();
            }
    }}

    @Override public boolean remove(int value) {
        while(true){
            Node pred = head, curr = head.next;
            while (curr.key < value) {
                pred = curr;
                curr = curr.next;
            }
            if (curr.key != value) {
                return false;
            }

            pred.lock.lock();
            curr.lock.lock();
            try {
                if (edgeExists(pred, curr)) {
                    pred.next = curr.next;
                    return true;
                }
            }
            finally {
                pred.lock.unlock();
                curr.lock.unlock();
            }

    }}

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

    // Dadas referencias a dos nodos pred --> curr, retorna true si sigue existiendo la relación pred --> curr en la lista
    private boolean edgeExists(Node pred, Node curr) {
        Node node = head;
        while (node.key <= pred.key) {
            if (node == pred)
                return pred.next == curr;
            node = node.next;
        }
        return false;
    }

    private static final class Node {
        final long key;
        volatile Node next;
        final ReentrantLock lock = new ReentrantLock();
        Node(long key) { this(key, null); }
        Node(long key, Node next) { this.key=key; this.next=next; } }
}
