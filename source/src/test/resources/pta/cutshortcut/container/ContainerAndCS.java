import java.util.ArrayList;

public class ContainerAndCS {
    public static void main(String[] args) {
        testContainerCS();
    }

    public static void testContainerCS() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        ArrayList<A> al1 = new ArrayList<A>();
        ArrayList<A> al2 = new ArrayList<A>();
        al1.add(a1);
        al2.add(a3);
        foo(al1, a2);
        foo(al2, a4);

        A r1 = al1.get(0);
        A r2 = al2.get(0);

        PTAAssert.sizeEquals(3, r1, r2);
    }

    public static void foo(ArrayList<A> al, A source) {
        al.add(source);
    }
}

class A {

}
