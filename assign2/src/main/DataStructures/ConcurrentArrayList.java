package main.DataStructures;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentArrayList<T> {

    private final ArrayList<T> list;
    private final Lock lock;

    public ConcurrentArrayList() {
        list = new ArrayList<>();
        lock = new ReentrantLock();
    }

    public void add(T item) {
        lock.lock();
        try {
            list.add(item);
        } finally {
            lock.unlock();
        }
    }
    public void remove(T item) {
        lock.lock();
        try {
            list.remove(item);
        } finally {
            lock.unlock();
        }
    }
    public T get(int index) {
        return list.get(index);
    }

    public ArrayList<T> getList() {
        return list;
    }
    public int size() {
        return list.size();
    }

    public void clear() {
        lock.lock();
        try {
            list.clear();
        } finally {
            lock.unlock();
        }
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }
}
