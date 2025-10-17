class MultiFieldStore {

    public static void main(String[] args) {
        testMultiStore();
    }

    static void testMultiStore() {
        A a11 = new A();
        A a12 = new A();
        A a21 = new A();
        A a22 = new A();
        multiStore(a11, "s1", a12);
        multiStore(a21, "s2", a22);
        String r1 = a11.getName();
        String r2 = a12.getName();
        String r3 = a21.getName();
        String r4 = a22.getName();
//        PTAAssert.sizeEquals(1, r1, r3);
//        PTAAssert.sizeEquals(2, r2, r4);
    }

    static void multiStore(A a1, String n1, A a2) {
        a1.name = n1;
        a2.name = n1;
        a2 = new A();
    }

    static String multiLoad(A a1, String n, A a2) {
        n = a1.name;
        n = a2.name;
        String nx;
        nx = a1.name;
        nx = a2.name;
        nx = a2.name;
        return n;
    }

}

class A{
    public String name;

    String getName() {
        return name;
    }
}
