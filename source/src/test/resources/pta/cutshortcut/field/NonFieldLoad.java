class NonFieldLoad {

    public static void main(String[] args) {
        testNonFieldLoad1();
        testNonFieldLoad2();
        testNonFieldLoad3();
        testNonFieldLoad4();
        testNonFieldLoad5();
        testNonFieldLoad6();
        testNonFieldLoadStatic();
    }

    static void testNonFieldLoad1() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName1();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName1();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldLoad2() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName2();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName2();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldLoad3() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName3();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName3();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }

    static void testNonFieldLoad4() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName4();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName4();

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(4, s1, s2);
    }

    static void testNonFieldLoad5() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName5();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName5();

        PTAAssert.disjoint(s1, s2);
        PTAAssert.sizeEquals(1, s1, s2);
    }

    static void testNonFieldLoad6() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = a1.getName6();

        A a2 = new A();
        a2.setName("a2");
        String s2 = a2.getName6();

        PTAAssert.disjoint(s1, s2);
        PTAAssert.sizeEquals(1, s1, s2);
    }

    static void testNonFieldLoadStatic() {
        A a1 = new A();
        a1.setName("a1");
        String s1 = A.getName(a1);

        A a2 = new A();
        a2.setName("a2");
        String s2 = A.getName(a2);

        PTAAssert.equals(s1, s2);
        PTAAssert.sizeEquals(3, s1, s2);
    }
}

class A {
    static String s = "s";

    String name;

    static String fun() {
        return "x2";
    }

    void setName(String name) {
        this.name = name;
    }

    String getName1() {
        String r = "x1";
        r = this.name;
        return r;
    }

    String getName2() {
        String r = A.s;
        r = this.name;
        return r;
    }

    String getName3() {
        String r = fun();
        r = this.name;
        return r;
    }

    String getName4() {
        String[] array = {"x3", "x4"};
        String r = array[0];
        r = this.name;
        return r;
    }

    String getName5() {
        String r = this.name;
        String t = r;
        return t;
    }
    //a simple one layer implemenation asks the return value to be not redefined
    //so situations like getName5() and getName6() can not benifit from this pattern.
    //Or, a def-use analysis can be performed to gain precision from these situations.

    String getName6() {
        String t = this.name;
        String r = this.name;
        r = t;
        return r;
    }

    static String getName(A a) {
        a = new A(); // if a is redefined, then we do not apply load pattern.
        a.setName("x5");
        String r = a.name;
        return r;
    }
}
