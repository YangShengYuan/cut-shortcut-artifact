import java.lang.reflect.Method;
import java.util.ArrayList;

public class ReflectiveContainer {

    public static void main(String[] args) {
        testReflectiveAllocation();
        testReflectiveExit();
        testReflectiveEntrance();
    }

    static void testReflectiveAllocation() {
        try{
            Object o = new Object();
            ArrayList<A> empty = new ArrayList<A>();
            Class clz = empty.getClass();
            Class objClass = o.getClass();
            A a1 = new A();
            Object arrayList =  clz.newInstance();
            Method entrance = clz.getDeclaredMethod("add", objClass);
            entrance.invoke(arrayList, a1);
            Method exit = clz.getDeclaredMethod("get", int.class);
            Object r1 = exit.invoke(arrayList, 0);
            PTAAssert.sizeEquals(1, r1);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectiveExit() {
        try{
            A a1 = new A();
            A a2 = new A();
            ArrayList<A> arrayList =  new ArrayList<A>();
            arrayList.add(a1);
            arrayList.add(a2);
            Method exit = (arrayList.getClass()).getDeclaredMethod("get", int.class);
            Object r1 = exit.invoke(arrayList, 0);
            PTAAssert.sizeEquals(2, r1);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void testReflectiveEntrance() {
        try{
            A a1 = new A();
            A a2 = new A();
            ArrayList<A> arrayList =  new ArrayList<A>();
            Method entrance = (arrayList.getClass()).getDeclaredMethod("add", Object.class);
            entrance.invoke(arrayList, a1);
            entrance.invoke(arrayList, a2);
            Object r1 = arrayList.get(0);
            PTAAssert.sizeEquals(2, r1);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class A {

}
