class BasicFieldStore {

    public static void main(String[] args) {
        testThis();
        testParam();
    }

    static void testThis() {
        A a1 = new A();
        String name1 = "a1";
        a1.setName(name1);

        A a2 = new A();
        String name2 = "a2";
        a2.setName(name2);

        PTAAssert.equals(a1.name, name1);
        PTAAssert.equals(a2.name, name2);
        PTAAssert.disjoint(a1.name, a2.name);
    }

    static void testParam() {
        A a1 = new A();
        String name1 = "a1";
        A.setName(a1, name1);

        A a2 = new A();
        String name2 = "a2";
        A.setName(a2, name2);

        PTAAssert.equals(a1.name, name1);
        PTAAssert.equals(a2.name, name2);
        PTAAssert.disjoint(a1.name, a2.name);
    }
}

class A {
    String name;

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    static String getName(A a) {
        return a.name;
    }

    static void setName(A a, String name) {
        a.name = name;
    }
}
