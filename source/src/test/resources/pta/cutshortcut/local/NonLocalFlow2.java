class NonLocalFlow2 {

    public static void main(String[] args) {
        testLoadStatic();
        testLoadArray();
        testHeapAllocation();
        testInvocation();
        testCastNonLocalFlow();
        testAssignParameter();
    }

    static void testLoadStatic() {
        A a = new A();
        String s1 = a.loadStaticField("s1");
        String s2 = a.loadStaticField("s2");
        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testLoadArray() {
        A a = new A();
        String[] array = {"s3", "s4"};
        String s1 = a.loadArrayIndex("s1", array);
        String s2 = a.loadArrayIndex("s2", array);
        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(4, s1, s2);
    }

    static void testHeapAllocation() {
        A a = new A();
        C c1 = new C();
        C c2 = new C();
        C r1 = a.assignHeapAllocation(c1);
        C r2 = a.assignHeapAllocation(c2);
        PTAAssert.equals(r1, r2);
        PTAAssert.sizeEquals(3, r1, r2);
    }

    static void testInvocation() {
        A a = new A();
        String s1 = a.assignInvocationReturn("s1");
        String s2 = a.assignInvocationReturn("s2");
        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testCastNonLocalFlow() {
        A a = new A();
        B b = new B();
        Object s1 = a.castWithFieldLoad("s1", b);
        Object s2 = a.castWithFieldLoad("s2", b);
        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testAssignParameter() {
        A a = new A();
        String s1 = a.assignParameter("s1");
        String s2 = a.assignParameter("s2");
        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }
}

class A {

    static String fun() {
        return "x2";
    }

    String loadStaticField(String p) {
        String r = p;
        p = B.s;
        return p;
    }

    String loadArrayIndex(String p, String[] array) {
        String r = p;
        r = array[0];
        return r;
    }

    C assignHeapAllocation(C c) {
        C r = c;
        r = new C();
        return r;
    }

    String assignInvocationReturn(String p) {
        String r = p;
        r = fun();
        return r;
    }

    Object castWithFieldLoad(Object o, B b) {
        String r = (String) o;
        r = B.s;
        return r;
    }

    String assignParameter(String p) {
        p = "x3";
        String r = p;
        return r;
    }
}

class B {
    static String s = "x1";
}

class C {
}
