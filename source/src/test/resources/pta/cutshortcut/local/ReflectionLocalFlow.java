import java.lang.reflect.*;

public class ReflectionLocalFlow {

    public static void main(String[] args) {
        testReflectionArg();
        testReflectionArgStatic();
        testReflectionMultiArg();
        testReflectionThis();
        testReflectionConstructor();
    }

    static void testReflectionArg() {
        try {
            A a = new A();
            Method mtd = a.getClass().getDeclaredMethod("localFlowParam", String.class);
            Object r1 = mtd.invoke(a, "s1");
            Object r2 = mtd.invoke(a, "s2");
            PTAAssert.sizeEquals(1, r1, r2);
            PTAAssert.disjoint(r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionArgStatic() {
        try {
            A a = new A();
            Method mtd = a.getClass().getDeclaredMethod("localFlowParamStatic", String.class);
            Object r1 = mtd.invoke(null, "s1");
            Object r2 = mtd.invoke(null, "s2");
            PTAAssert.sizeEquals(1, r1, r2);
            PTAAssert.disjoint(r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionMultiArg() {
        try {
            A a = new A();
            Method mtd = a.getClass().getDeclaredMethod("localFlowMultiParam", String.class, String.class);
            Object r1 = mtd.invoke(a, "s1", "s2");
            Object r2 = mtd.invoke(a, "s3", "s4");
            PTAAssert.sizeEquals(2, r1, r2);
            PTAAssert.disjoint(r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionThis() {
        try {
            A a1 = new A();
            A a2 = new A();
            A a = new A();
            Method mtd = a.getClass().getDeclaredMethod("localFlowThis");
            Object r1 = mtd.invoke(a1);
            Object r2 = mtd.invoke(a2);
            PTAAssert.sizeEquals(1, r1, r2);
            PTAAssert.disjoint(r1, r2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectionConstructor() {
        try {
            Class clz = Class.forName("A");
            Object a11 = clz.newInstance();
            Object a12 = clz.newInstance();
            Object a21 = clz.getDeclaredConstructor().newInstance();
            Object a22 = clz.getDeclaredConstructor().newInstance();
            Constructor constructor = clz.getConstructor(String.class);
            Object a31 = constructor.newInstance("a1");
            Object a32 = constructor.newInstance("a2");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class A {

    A() {}

    A(String p) {}

    String localFlowParam(String p) {
        String r = p;
        return r;
    }

    static String localFlowParamStatic(String p) {
        String r = p;
        return r;
    }

    String localFlowMultiParam(String p1, String p2) {
        String r1 = p1;
        String r2 = p2;
        String r3 = r2;
        String r = r1;
        r = r3;
        return r;
    }

    A localFlowThis() {
        A r = this;
        return r;
    }
}
