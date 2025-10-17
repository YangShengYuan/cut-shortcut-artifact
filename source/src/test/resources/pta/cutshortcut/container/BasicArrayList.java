import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;

class BasicArrayList {
    public static void main(String[] args) {
        testList();
        testList2();
        testIterator();
        testListIterator();
        testSubList();
        testSubListIterator();
        testSubListListIterator();
        testListCollectivelyAdd();
        testGenerics();
    }

    static void testList() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        l1.add(a1);
        l2.add(a2);
        A r1 = l1.get(0);//pt(r1) = o1
        A r2 = l2.get(0);//pt(r2) = o2

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
    }

    static void testList2() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        l1.add(a1);
        l2.add(a2);
        A r1 = l1.get(0);//pt(r1) = {o1,o3}
        A r2 = l2.get(0);//pt(r2) = {o2}
        A a3 = new A();//o3
        l1.add(a3);

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r1);
        PTAAssert.sizeEquals(1, r2);
    }

    static void testIterator() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();
        A a2 = new A();
        l1.add(a1);
        l2.add(a2);
        Iterator<A> itr1 = l1.iterator();
        A r1 = itr1.next();//pt(r1) = o1
        Iterator<A> itr2 = l2.iterator();
        A r2 = itr2.next();//pt(r2) = o2
        Iterator<A> itr3 = itr2;
        itr3 = itr1;
        A r3 = itr3.next();//pt(r3) = o1, o2

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
        PTAAssert.sizeEquals(2, r3);
    }

    static void testListIterator() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        A a5 = new A();//o5
        A a6 = new A();//o6
        l1.add(a1);
        l2.add(a2);

        ListIterator<A> itr1 = l1.listIterator();
        A r1 = itr1.next();
        itr1.set(a4);
        itr1.add(a3);
        A r2 = l1.get(0); //pt(r1) = pt(r2) = {o1,o3,o4}

        ListIterator<A> itr2 = l2.listIterator();
        A r3 = itr2.next();
        itr2.set(a5);
        itr2.set(a6);
        A r4 = l2.get(0); //pt(r3) = pt(r4) = {o2,o5,o6}

        PTAAssert.equals(r1, r2);
        PTAAssert.equals(r3, r4);
        PTAAssert.sizeEquals(3, r1, r3);
        PTAAssert.disjoint(r1, r3);
    }

    static void testSubList() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        A a5 = new A();//o5
        A a6 = new A();//o6
        A a7 = new A();//o7
        l1.add(a1);
        l1.add(a2);
        l2.add(a3);
        l2.add(a4);
        List<A> subL1 = l1.subList(0,1);
        subL1.add(a5);
        subL1.set(0,a6);
        subL1.add(0,a7);
        A r1 = subL1.get(0);//pt(r1) = {o1,o2,o5,o6,o7}
        A r2 = l1.get(0);   //pt(r2) = {o1,o2,o5,o6,o7}

        List<A> subL2 = l2.subList(0,1);
        A r3 = subL2.get(0);//pt(r3) = {o3,o4}
        A r4 = l2.get(0);   //pt(r4) = {o3,o4}

        PTAAssert.equals(r1, r2);
        PTAAssert.equals(r3, r4);
        PTAAssert.sizeEquals(5, r1, r2);
        PTAAssert.sizeEquals(2, r3, r4);
        PTAAssert.disjoint(r1, r3);
    }

    static void testSubListIterator() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        l1.add(a1);
        l1.add(a2);
        l2.add(a3);
        l2.add(a4);
        List<A> subL1 = l1.subList(0,1);
        Iterator<A> iterator1 = subL1.iterator();
        A r1 = iterator1.next();//pt(r1) = {o1,o2}

        List<A> subL2 = l2.subList(0,1);
        Iterator<A> iterator2 = subL2.iterator();
        A r2 = iterator2.next();//pt(r2) = {o3,o4}

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testSubListListIterator() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        A a5 = new A();//o5
        A a6 = new A();//o6
        A a7 = new A();//o7
        A a8 = new A();//o8
        l1.add(a1);
        l1.add(a2);
        l2.add(a3);
        l2.add(a4);

        List<A> subL1 = l1.subList(0,1);
        ListIterator<A> iterator1 = subL1.listIterator();
        A r1 = iterator1.next();
        iterator1.set(a5);
        iterator1.add(a6);
        A r2 = subL1.get(0);
        A r3 = l1.get(0);
        //pt(r1) = pt(r2) = pt(r3) = {o1,o2,o5,o6}

        List<A> subL2 = l2.subList(0,1);
        ListIterator<A> iterator2 = subL2.listIterator();
        A r4 = iterator2.next();
        iterator2.set(a7);
        iterator2.add(a8);
        A r5 = subL2.get(0);
        A r6 = l2.get(0);
        //pt(r4) = pt(r4) = pt(r6) = {o3,o4,o7,o8}

        PTAAssert.equals(r1, r2, r3);
        PTAAssert.equals(r4, r5, r6);
        PTAAssert.disjoint(r1, r4);
        PTAAssert.sizeEquals(4, r1, r4);
    }

    static void testListCollectivelyAdd() {
        List<A> l1 = new ArrayList<A>();
        List<A> l2 = new ArrayList<A>();
        List<A> l3 = new ArrayList<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        l1.add(a1);
        l1.add(a2);
        l2.add(a3);
        l3.add(a4);
        l2.addAll(l1);
        l3.addAll(1,l2);
        A r1 = l1.get(0); //pt(r1) = o1,o2
        A r2 = l2.get(0); //pt(r2) = o1,o2,o3
        A r3 = l3.get(0); //pt(r2) = o1,o2,o3,o4

        PTAAssert.sizeEquals(2, r1);
        PTAAssert.sizeEquals(3, r2);
        PTAAssert.sizeEquals(4, r3);
        PTAAssert.contains(r3, r2, r1);
    }

    static void testGenerics() {
        ArrayList<Object> al1 = new ArrayList<Object>();
        ArrayList<A> al2 = new ArrayList<A>();
        ArrayList<B> al3 = new ArrayList<B>();
        B a1 = new B();//o1
        B a2 = new B();//o2
        B a3 = new B();//o3
        B a4 = new B();//o4
        B a5 = new B();//o5

        al3.add(a1);
        al2.add(a2);
        al1.add(a3);

        al1.addAll(al2);
        al2.addAll(al3);

        al1.add(a4);
        al3.add(a5);

        //al1 : o1, o2, o3, o4, o5
        //al2 : o1, o2, o5
        //al3 : o1, o5
        B r1 = (B) al1.get(0);
        B r2 = (B) al2.get(0);
        B r3 = al3.get(0);

        PTAAssert.sizeEquals(5, r1);
        PTAAssert.sizeEquals(3, r2);
        PTAAssert.sizeEquals(2, r3);
        PTAAssert.contains(r1, r2, r3);
        PTAAssert.contains(r2, r3);
    }
}

class A{
}

class B extends A {}

