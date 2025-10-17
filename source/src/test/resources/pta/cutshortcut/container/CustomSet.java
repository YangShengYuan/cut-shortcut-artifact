import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class CustomSet {
    public static void main(String[] args) {
        testCustomSet1();
        testCustomSet2();
    }

    static void testCustomSet1() {
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4

        MySet<A> mySet = new MySet<A>();
        mySet.add(a1);

        ArrayList<A> list1 = new ArrayList<A>(); // Host1
        list1.add(a2);
        list1.addAll(mySet); //taint host1

        ArrayList<A> list2 = new ArrayList<A>(); // Host2
        list2.add(a3);

        ArrayList<A> list3 = new ArrayList<A>(); // Host3
        list3.add(a4);
        list3.addAll(list1); //taint host3

        //myset -> list1 -> list3
        //list2

        A r1 = list1.get(0); //pt(r1) = o1 o2 o3 o4, and o1~o2 from testCustomSet2
        A r2 = list2.get(0); //pt(r2) = o2
        A r3 = list3.get(0); //pt(r3) = o1 o2 o3 o4, and o1~o2 from testCustomSet2

        PTAAssert.sizeEquals(6, r1, r3);
        PTAAssert.sizeEquals(1, r2);
    }

    static void testCustomSet2() {
        A a1 = new A();//o1
        A a2 = new A();//o2
        ArrayList<A> list1 = new ArrayList<A>();
        list1.add(a1);
        ArrayList<A> list2 = new ArrayList<A>();
        list1.add(a2);
        list2.addAll(list1);
        A r = list2.get(0); // o1, o2

        PTAAssert.sizeEquals(2, r);
    }
}

class MySet<E> implements Collection<E> {

    private HashSet<E> innerSet = new HashSet<E>();

    @Override
    public int size() {
        return innerSet.size();
    }

    @Override
    public boolean isEmpty() {
        return innerSet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return innerSet.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return innerSet.iterator();
    }

    @Override
    public Object[] toArray() {
        return innerSet.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return innerSet.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return innerSet.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return innerSet.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return innerSet.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return innerSet.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return innerSet.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return innerSet.retainAll(c);
    }

    @Override
    public void clear() {
        innerSet.clear();
    }
}

class A {

}
