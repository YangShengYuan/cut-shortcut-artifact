class ComplexLocalFlow {

    public static void main(String[] args) {
        testMultiParams0();
        testMultiParams1();
        testMultiParams2();
        testMultiParams3();
        testMultiParamAndThis();
        testMultiParamMultiReturn();
        testAssignNull();
        testComplexCast();
        testAssignCycle1();
        testAssignCycle2();
    }

    static void testMultiParams0() {
        A a = new A();
        String r1 = a.multiParams0("s1", "s2", "s3");
        String r2 = a.multiParams0("s4", "s5", "s6");
        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testMultiParams1() {
        A a = new A();
        String r1 = a.multiParams1("s1", "s2", "s3");
        String r2 = a.multiParams1("s4", "s5", "s6");
        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testMultiParams2() {
        A a = new A();
        String r1 = a.multiParams2("s1", "s2", "s3");
        String r2 = a.multiParams2("s4", "s5", "s6");
        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testMultiParams3() {
        A a = new A();
        String r1 = a.multiParams3("s1", "s2", "s3");
        String r2 = a.multiParams3("s4", "s5", "s6");
        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testMultiParamAndThis() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        A a5 = new A();
        A a6 = new A();
        A r1 = a1.multiParamAndThis(a2, a3);
        A r2 = a4.multiParamAndThis(a5, a6);
        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testMultiParamMultiReturn() {
        A a = new A();
        String r1 = a.multiParamMultiReturn("s1", "s2", "s3", 2);
        String r2 = a.multiParamMultiReturn("s4", "s5", "s6", 1);
        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testAssignNull() {
        A a = new A();
        String r1 = a.assignNull("s1", "s2");
        String r2 = a.assignNull("s3", "s4");
        PTAAssert.sizeEquals(4, r1, r2);
        PTAAssert.equals(r1, r2);
    }

    static void testComplexCast() {
        A a = new A();
        Object o1 = "o1";
        Object o2 = "o2";
        Object o3 = "o3";
        Object o4 = "o4";
        Object r1 = a.complexCast(o1, o2, "s1");
        Object r2 = a.complexCast(o3, o4, "s2");
        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testAssignCycle1() {
        A a = new A();
        String r1 = a.assignCycle1("s1", "s2");
        String r2 = a.assignCycle1("s3", "s4");
        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testAssignCycle2() {
        A a = new A();
        String r1 = a.assignCycle2("s1", "s2");
        String r2 = a.assignCycle2("s3", "s4");
        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }
}

class A {

    String multiParams0(String p1, String p2, String p3) {
        String t1 = p1;
        String t2 = p1;
        t2 = p2;
        t2 = p3;
        String t3 = t2;
        String r = t1;
        r = t3;
        return r; //r contains p1, p2, p3.
        // this test can pass now.
    }

    String multiParams1(String p1, String p2, String p3) {
        String t1 = p1;
        p2 = p1;
        p2 = p3;
        String t2 = p2;
        String r = t1;
        r = t2;
        return r; //r contains p1, p2, p3.
        // when assign some thing to a parameter, some thing goes wrong.
        // This cases can be easily handled in Doop, maybe in Tai-e we need further specific handling due to a different IR.
    }

    String multiParams2(String p1, String p2, String p3) {
        String r = p1;
        p1 = p3;
        p2 = p3;
        r = p3;
        return r; //r containts p1, p3
        // same problem as multiParams1
    }

    String multiParams3(String p1, String p2, String p3) {
        p2 = p1;
        p3 = p2;
        String p4 = p3;
        String p5 = p4;
        String r = p5;
        return r; //r contains p1, p2, p3
        // same problem as multiParams1
    }

    A multiParamAndThis(A a1, A a2) {
        A t = this;
        A r = t;
        r = a1;
        return r; //r contains this and a1
    }

    String multiParamMultiReturn(String p1, String p2, String p3, int x) {
        String r1 = p1;
        if (x % 2 == 0) {
            String r2 = p2;
            r2 = r1;
            return r2;
        } else {
            String r3 = p3;
            return r3;
        }
        // return value contains p1, p2, p3.
    }

    String assignNull(String p1, String p2) {
        String r = p1;
        r = null;
        p2 = null;
        r = p2;
        return r; // r contains p1, p2 .
        // if we deal with "assign null" specially, this case will be more precise.
    }

    Object complexCast(Object o1, Object o2, String s) {
        String t1 = (String) o1;
        Object r1 = t1;
        String t2 = (String) o2;
        Object r2 = t2;
        Object r = r1;
        r = r2;
        r = s;
        return r; //r contains o1, o2, s
    }

    String assignCycle1(String p1, String p2) {
        String a = p2;
        p2 = p1;
        p1 = a;
        String r = p2;
        return r; //causes infinite loop
    }

    String assignCycle2(String p1, String p2) {
        String t2 = p2;
        String t1 = p1;
        String a = t2;
        t2 = t1;
        t1 = a;
        String r = t2;
        return r;
    }
}

