import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

public class UtilMethods {
    public static void main(String[] args) {
        testUtilTransfer();
        testUtilArrayEn();
        testUtilBatchEn();
        testUtilEntrance();
    }

    public static void testUtilTransfer() {
        A a1 = new A();
        A a2 = new A();

        ArrayList<A> list1 = new ArrayList<A>();
        ArrayList<A> list2 = new ArrayList<A>();
        list1.add(a1);
        list2.add(a2);

        Enumeration<A> enum1 = Collections.enumeration(list1);
        Enumeration<A> enum2 = Collections.enumeration(list2);
        Object r1 = enum1.nextElement();
        Object r2 = enum2.nextElement();

        PTAAssert.sizeEquals(1, r1, r2);
    }

    public static void testUtilArrayEn() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        ArrayList<A> list1 = new ArrayList<A>();
        ArrayList<A> list2 = new ArrayList<A>();

        Collections.addAll(list1, a1, a2);
        Collections.addAll(list2, a3, a4);

        A r1 = list1.get(0);
        A r2 = list2.get(0);

        PTAAssert.sizeEquals(2, r1, r2);
    }

    public static void testUtilBatchEn() {
        A a1 = new A();
        A a2 = new A();

        ArrayList<A> list1 = new ArrayList<A>();
        ArrayList<A> list2 = new ArrayList<A>();
        list1.add(a1);
        list2.add(a2);

        Collections.copy(list2, list1);

        A r1 = list1.get(0);
        A r2 = list2.get(0);

        PTAAssert.sizeEquals(1, r1);
        PTAAssert.sizeEquals(2, r2);
    }

    public static void testUtilEntrance() {
        A a1 = new A();
        A a2 = new A();

        ArrayList<A> list1 = new ArrayList<A>();
        list1.add(a1);

        Collections.fill(list1, a2);

        A r1 = list1.get(0);

        PTAAssert.sizeEquals(2, r1);
    }
}

class A {
}