import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class ContainerReentrancy {
    public static void main(String[] args) {
        testReentrancy1();
        testReentrancy2();
        testReentrancy3();
        testReentrancy4();
        testReentrancy5();
    }

    public static void testReentrancy1() {
        //Vector
        Vector<A> vector1 = new Vector<A>();
        Vector<A> vector2 = new Vector<A>();
        A a1 = new A();
        A a2 = new A();
        vector1.add(0, a1);
        vector2.add(0, a2);

        A r1 = vector1.get(0); // a1
        A r2 = vector2.get(0); // a2

        PTAAssert.sizeEquals(1, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    public static void testReentrancy2() {
        //AbstractQueue addAll
        ArrayList<A> al = new ArrayList<A>();
        AbstractQueue<A> abstractQueue1 = new PriorityQueue<A>();
        AbstractQueue<A> abstractQueue2 = new PriorityQueue<A>();
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        al.add(a1);
        al.add(a2);
        abstractQueue1.add(a3);
        abstractQueue2.add(a4);

        abstractQueue1.addAll(al);

        A r1 = abstractQueue1.element(); // a1, a2, a3
        A r2 = abstractQueue2.element(); // a4

        PTAAssert.sizeEquals(3, r1);
        PTAAssert.sizeEquals(1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    public static void testReentrancy3() {
        //AbstractList$ListItr
        AbstractList<A> abstractList1 = new ArrayList<A>();
        ListIterator<A> iterator1 = abstractList1.listIterator();

        AbstractList<A> abstractList2 = new ArrayList<A>();
        ListIterator<A> iterator2 = abstractList2.listIterator();

        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        abstractList1.add(a1);
        abstractList2.add(a2);
        iterator1.add(a3);
        iterator2.add(a4);

        A r1 = abstractList1.get(0); // a1, a3
        A r2 = abstractList2.get(0); // a2, a4

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    public static void testReentrancy4() {
        //HashMap
        Map<A,B> map = new HashMap<A,B>();
        HashMap<A,B> hashMap1 = new HashMap<A,B>();
        HashMap<A,B> hashMap2 = new HashMap<A,B>();

        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        B b1 = new B();
        B b2 = new B();
        B b3 = new B();
        B b4 = new B();

        map.put(a1,b1);
        map.put(a2,b2);
        hashMap1.put(a3,b3);
        hashMap2.put(a4,b4);
        hashMap1.putAll(map);

        B r1 = hashMap1.get(a1); // b1, b2, b3
        B r2 = hashMap2.get(a4); // b4

        PTAAssert.sizeEquals(3, r1);
        PTAAssert.sizeEquals(1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    public static void testReentrancy5() {
        //ArrayBlockingQueue
        ArrayBlockingQueue<A> q1 = new ArrayBlockingQueue<A>(10);
        ArrayBlockingQueue<A> q2 = new ArrayBlockingQueue<A>(10);
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q1.add(a2);
        q2.add(a3);
        q2.add(a4);
        Iterator<A> itr1 = q1.iterator();
        A r1 = itr1.next(); // pt(r1) = {o1, o2}
        Iterator<A> itr2 = q2.iterator();
        A r2 = itr2.next(); // pt(r2) = {o3, o4}

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }
}

class A{

}

class B{

}
