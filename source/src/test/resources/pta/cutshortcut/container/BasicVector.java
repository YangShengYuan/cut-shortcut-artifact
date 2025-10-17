import java.util.Vector;

public class BasicVector {
    public static void main(String[] args) {
        testVector1();
        testVector2();
    }

    public static void testVector1() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        A a5 = new A();
        A a6 = new A();
        A a7 = new A();
        A a8 = new A();
        A a9 = new A();

        Vector<A> v = new Vector<A>();
        v.setElementAt(a1, 0);
        v.insertElementAt(a2, 1);
        v.addElement(a3);
        v.set(0, a4);
        v.add(0, a5);
        v.add(a6);

        Vector<A> s1 = new Vector<A>();
        s1.add(a7);
        v.addAll(s1);

        Vector<A> s2 = new Vector<A>();
        s2.add(a8);
        v.addAll(0, s2);

        Vector<A> large = new Vector<A>(v);
        large.add(a9);

        A r1 = v.elementAt(0);     // 1-8
        A r2 = s1.elementAt(0);    // a7
        A r3 = s2.elementAt(0);    // a8
        A r4 = large.elementAt(0); // 1-9

        PTAAssert.disjoint(r2, r3);
        PTAAssert.sizeEquals(8, r1);
        PTAAssert.sizeEquals(9, r4);
    }

    public static void testVector2() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        Vector<A> v1 = new Vector<A>();
        v1.addElement(a1);
        v1.addElement(a2);

        Vector<A> v2 = (Vector<A>) v1.clone();
        v2.addElement(a3);
        v2.addElement(a4);

        A r1 = v1.get(0);
        A r2 = v2.get(0);
    }
}

class A {

}
