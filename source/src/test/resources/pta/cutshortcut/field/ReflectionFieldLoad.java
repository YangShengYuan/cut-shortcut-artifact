import java.lang.reflect.Method;

class ReflectionFieldLoad {

    public static void main(String[] args) {
        testReflectionParam();
        testReflectionThis();
    }

    static void testReflectionThis() {
        try {
            A a1 = new A();
            a1.setName("a1");
            Method mtd = a1.getClass().getDeclaredMethod("getName");
            Object r1 = mtd.invoke(a1);

            A a2 = new A();
            a2.setName("a2");
            Object r2 = mtd.invoke(a2);

            PTAAssert.disjoint(r1, r2);
            PTAAssert.sizeEquals(1, r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionParam() {
        try {
            A a1 = new A();
            a1.setName("a1");
            Method mtd = a1.getClass().getDeclaredMethod("getName", A.class);
            Object r1 = mtd.invoke(null, a1);

            A a2 = new A();
            a2.setName("a2");
            Object r2 = mtd.invoke(null, a2);

            PTAAssert.disjoint(r1, r2);
            PTAAssert.sizeEquals(1, r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
