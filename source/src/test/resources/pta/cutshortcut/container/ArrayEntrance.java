import java.util.concurrent.CopyOnWriteArrayList;

public class ArrayEntrance {
    public static void main(String[] args) {
        testInitializeFromArray1();
        testInitializeFromArray2();
        testInitializeFromArray3();
    }

    public static void testInitializeFromArray1() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        A[] array1 = {a1, a2};
        Object[] array2 = new Object[2];
        array2[0] = a3;
        array2[1] = a4;

        CopyOnWriteArrayList<Object> list1 = new CopyOnWriteArrayList<Object>(array1);
        CopyOnWriteArrayList<Object> list2 = new CopyOnWriteArrayList<Object>(array2);

        A r1 = (A) list1.get(0);     // r1: {a1, a2}
        A r2 = (A) list2.get(0);     // r2: {a3, a4}

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r1, r2);
    }

    public static void testInitializeFromArray2() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        A a5 = new A();
        A a6 = new A();
        A a7 = new A();
        A a8 = new A();

        A[] array1 = {a1, a2};
        A[] array2 = {a5, a6};
        A[] array3 = {a7, a8};

        CopyOnWriteArrayList<Object> list1 = new CopyOnWriteArrayList<Object>(array1);
        CopyOnWriteArrayList<Object> list2 = new CopyOnWriteArrayList<Object>(array3);
        list1.add(a3);
        list2.add(a4);

        A r1 = (A) list1.get(0); // r1 : {a1, a2, a5, a6, a3}
        A r2 = (A) list2.get(0); // r2 : {a7, a8, a4}

        array1 = array2;

        PTAAssert.sizeEquals(5, r1);
        PTAAssert.sizeEquals(3, r2);
        PTAAssert.disjoint(r1, r2);
    }

    public static void testInitializeFromArray3() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        A a5 = new A();
        A a6 = new A();

        A[] array1 = {a1, a2};
        A[] array2 = {a3, a4};
        A[] array3 = {a5, a6};

        CopyOnWriteArrayList<Object> list1 = new CopyOnWriteArrayList<Object>(array1);
        CopyOnWriteArrayList<Object> list2 = new CopyOnWriteArrayList<Object>(array2);
        CopyOnWriteArrayList<Object> list3 = new CopyOnWriteArrayList<Object>(array3);

        list2 = list1;
        list3.addAll(list2);

        A r1 = (A) list1.get(0); // r1 : {a1, a2}
        A r2 = (A) list2.get(0); // r2 : {a1, a2, a3, a4}
        A r3 = (A) list3.get(0); // r2 : {a1, a2, a3, a4, a5, a6}

        PTAAssert.sizeEquals(2, r1);
        PTAAssert.sizeEquals(4, r2);
        PTAAssert.sizeEquals(6, r3);
        PTAAssert.contains(r3, r1, r2);
    }
}

class A{

}
