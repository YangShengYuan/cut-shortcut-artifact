class BasicFieldLoad {

    public static void main(String[] args) {
//        testThis();
        testParam();
    }

    static void testThis() {
        A a1 = new A();
        P p1 = new P();
        a1.setName(p1);
        P s1 = a1.getName();

        A a2 = new A();
        P p2 = new P();
        a2.setName(p2);
        P s2 = a2.getName();

        PTAAssert.equals(s1, p1);
        PTAAssert.equals(s2, p2);
        PTAAssert.disjoint(s1, s2);
    }

    static void testParam() {
        A a1 = new A();
        P p1 = new P();
        A.setName(a1, p1);
        P s1 = A.getName(a1);

        A a2 = new A();
        P p2 = new P();
        A.setName(a2, p2);
        P s2 = A.getName(a2);

        PTAAssert.equals(s1, p1);
        PTAAssert.equals(s2, p2);
        PTAAssert.disjoint(s1, s2);
    }
}

class A {
    P name;

    P getName() {
        return name;
    }

    void setName(P name) {
        this.name = name;
    }

    static P getName(A a) {
        return a.name;
    }

    static void setName(A a, P name) {
        a.name = name;
    }
}

class P {

}
