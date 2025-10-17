import java.util.Arrays;
import java.util.List;

public class ArrayAsList {
    public static void main(String[] args) {
        testArrayAsList1();
        testArrayAsList2();
    }

    static void testArrayAsList1() {
        E e1 = new E();
        E e2 = new E();
        E e3 = new E();
        E e4 = new E();

        List<E> list1 = Arrays.asList(e1, e2);
        List<E> list2 = Arrays.asList(e3, e4);

        E r1 = list1.get(0);
        E r2 = list2.get(0);

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r1, r2);
    }

    static void testArrayAsList2() {
        E e1 = new E();
        E e2 = new E();
        E e3 = new E();
        E e4 = new E();

        E[] array1 = new E[] {e1, e2}; // 2 + 1 allocated in passingArrayElement
        E[] array2 = new E[] {e3, e4}; // 2 + 3 in array1

        List<E> list1 = Arrays.asList(array1);
        List<E> list2 = Arrays.asList(array2);

        E r1 = list1.get(0); // 3
        E r2 = list2.get(0); // 5 in jdk6, 9 in jdk 8
                             // because System.arrayCopy is called in library in jdk 8.

        passingArrayElement(array1);
        copyArray(array1, array2);

        PTAAssert.sizeEquals(3, r1);
        PTAAssert.sizeEquals(5, r2);
    }

    static void passingArrayElement(E[] array) {
        E ex = new E();
        array[0] = ex;
    }

    static void copyArray(E[] array1, E[] array2) {
        System.arraycopy(array1, 0, array2, 1, 2);
    }
}

class E {
}