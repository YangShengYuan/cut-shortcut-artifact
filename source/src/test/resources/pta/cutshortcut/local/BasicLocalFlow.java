class BasicLocalFlow {

    public static void main(String[] args) {
        testIdentity();
        testReturnP0();
        testReturnP0P1();
        testReturnThis();
    }

    static void testIdentity() {
        C c = new C();
        String s1 = c.identify("s1");
        String s2 = c.identify("s2");
        PTAAssert.instanceOfIn("java.lang.String", s1, s2);
        PTAAssert.disjoint(s1, s2);
    }

    static void testReturnP0() {
        C c = new C();
        String s1 = c.returnP0("s1", null);
        String s2 = c.returnP0("s2", null);
        PTAAssert.instanceOfIn("java.lang.String", s1, s2);
        PTAAssert.disjoint(s1, s2);
    }

    static void testReturnP0P1() {
        C c = new C();
        String s1 = c.returnP0P1("s1", "s11");
        String s2 = c.returnP0P1("s2", "s22");
        PTAAssert.sizeEquals(2, s1, s2);
        PTAAssert.disjoint(s1, s2);
    }

    static void testReturnThis() {
        C c1 = new C();
        C c2 = new C();
        Object o1 = c1.returnThis();
        Object o2 = c2.returnThis();
        PTAAssert.instanceOfIn("C", o1, o2);
        PTAAssert.disjoint(o1, o2);
    }
}

class C {

    String identify(String p) {
        return p;
    }

    String returnP0(String p0, String p1) {
        String s = p0;
        String ss = s;
        return ss;
    }

    String returnP0P1(String p0, String p1) {
        String s = p0;
        if (p0 != p1) {
            s = p1;
        }
        String ss = s;
        return ss;
    }

    C returnThis() {
        C c = this;
        return c;
    }
}
