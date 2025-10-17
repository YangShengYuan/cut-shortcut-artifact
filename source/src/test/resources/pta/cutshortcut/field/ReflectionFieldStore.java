import java.lang.reflect.Method;

class ReflectionFieldStore {

    public static void main(String[] args) {
        testReflectionParam();
        testReflectionThis();
    }

    static void testReflectionParam() {
        try {
            A a1 = new A();
            Method mtd = a1.getClass().getDeclaredMethod("setName", String.class);
            mtd.invoke(a1, "s1");
            String r1 = a1.getName();

            A a2 = new A();
            mtd.invoke(a2, "s2");
            String r2 = a2.getName();

            PTAAssert.disjoint(r1, r2);
            PTAAssert.sizeEquals(1, r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionThis() {
        try {
            A a1 = new A();
            Method mtd = a1.getClass().getDeclaredMethod("setName", A.class, String.class);
            mtd.invoke(null, a1, "s1");
            String r1 = a1.getName();

            A a2 = new A();
            mtd.invoke(null, a2, "s2");
            String r2 = a2.getName();

            PTAAssert.disjoint(r1, r2);
            PTAAssert.sizeEquals(2, r1, r2);
            // StoreHanlder do not add type filter,
            // {L30 new A} would be added into r1, r2
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

class A {
    String name;

    String getName() {
        String r = this.name;
        return r;
    }

    void setName(String name) {
        this.name = name;
    }

    static void setName(A a, String name) {
        a.name = name;
    }
}
