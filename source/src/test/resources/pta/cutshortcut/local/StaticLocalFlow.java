public class StaticLocalFlow {
    public static void main(String[] args) {
        testIdentity();
    }

    public static void testIdentity() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        A r1 = identity1(a1);
        A r2 = identity1(a2);
        A r3 = identity2("null", a3);
        A r4 = identity2("null", a4);
    }

    public static A identity1(A p) {
        return p;
    }

    public static A identity2(String s, A p2) {
        return p2;
    }
}

class A {

}
