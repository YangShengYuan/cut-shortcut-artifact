class NonFieldStore {

    public static void main(String[] args) {
        testNonFieldStore1();
        testNonFieldStore2();
        testNonFieldStore3();
        testNonFieldStore4();
        testNonFieldStore5();
        testNonFieldStore6();
        testNonFieldStoreStatic();
    }

    static void testNonFieldStore1() {
        A a1 = new A();
        a1.setName1("a1");
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName1("a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldStore2() {
        A a1 = new A();
        a1.setName2("a1");
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName2("a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldStore3() {
        A a1 = new A();
        a1.setName3("a1");
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName3("a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldStore4() {
        A a1 = new A();
        a1.setName4("a1");
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName4("a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(4, s1, s2);
    }

    static void testNonFieldStore5() {
        A a1 = new A();
        a1.setName5("a1");
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName5("a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(2, s1, s2);
    }

    static void testNonFieldStore6() {
        A p = new A();

        A a1 = new A();
        a1.setName6("a1", p);
        String s1 = a1.getName();

        A a2 = new A();
        a2.setName6("a2", p);
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(2, s1, s2);
    }

    static void testNonFieldStoreStatic() {
        A a1 = new A();
        A.setName(a1, "a1");
        String s1 = a1.getName();

        A a2 = new A();
        A.setName(a2, "a2");
        String s2 = a2.getName();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(2, s1, s2);
    }
}

class A {
    static String s = "s";

    String name;

    static String fun() {
        return "x2";
    }

    String getName() {
        return name;
    }

    void setName1(String name) {
        name = "x1";
        this.name = name;
    }

    void setName2(String name) {
        name = A.s;
        this.name = name;
    }

    void setName3(String name) {
        name = fun();
        this.name = name;
    }

    void setName4(String name) {
        String[] array = {"x3", "x4"};
        name = array[1];
        this.name = name;
    }

    void setName5(String name) {
        String temp = name;
        this.name = temp;
    }

    void setName6(String name, A p) {
        this.name = name;
        p.name = name;
        p = new A();
    }

    static void setName(A a, String name) {
        a = new A();
        a.name = name;
    }
}
