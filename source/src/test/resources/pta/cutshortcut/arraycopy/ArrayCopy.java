public class ArrayCopy {
    public static void main(String[] args) {
        testArrayCopy1();
        testArrayCopy2();
    }

    static void testArrayCopy1() {
        L l1 = new L();
        L l2 = new L();
        L l3 = new L();
        L l4 = new L();
        L[] array1 = new L[] {l1, l2};
        L[] array2 = new L[] {l3, l4};
        System.arraycopy(array1, 0, array2, 0, 1);
    }

    static void testArrayCopy2() {
        L l1 = new L();
        L l2 = new L();
        L l3 = new L();
        L l4 = new L();
        L[] array1 = new L[] {l1, l2};
        L[] array2 = new L[] {l3, l4};
        L[] temp = array1;
        temp = array2;
        System.arraycopy(temp, 0, temp, 0, 1);
    }

}

class L {}
