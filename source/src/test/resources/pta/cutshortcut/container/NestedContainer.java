import java.util.ArrayList;
import java.util.List;

public class NestedContainer {
    public static void main(String[] args) {
        testNestedContainer();
    }

    public static void testNestedContainer() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        List<List<A>> outer1 = new ArrayList<List<A>>();
        List<List<A>> outer2 = new ArrayList<List<A>>();
        List<A> inner1 = new ArrayList<A>();
        List<A> inner2 = new ArrayList<A>();
        List<A> inner3 = new ArrayList<A>();
        List<A> inner4 = new ArrayList<A>();

        outer1.add(inner1);
        outer1.add(inner2);
        outer2.add(inner3);
        outer2.add(inner4);

        inner1.add(a1);
        inner2.add(a2);
        inner3.add(a3);
        inner4.add(a4);

        List<A> r1 = outer1.get(0); // L17, L18
        List<A> r2 = outer2.get(0); // L19, L20
        A r3 = r1.get(0);           // a1, a2
        A r4 = r2.get(0);           // a3, a4

        PTAAssert.sizeEquals(2, r1, r2, r3, r4);
        PTAAssert.disjoint(r1, r2);
        PTAAssert.disjoint(r3, r4);
    }
}

class A {

}
