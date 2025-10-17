import java.util.Objects;
import java.util.stream.Stream;

public class StreamFromArray {

    public static void main(String[] args) {
        testStreamFromArray1();
        testStreamFromArray2();
    }

    public static void testStreamFromArray1() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();


        Stream<El> stream1 = Stream.of(e1, e2);
        Stream<El> stream2 = Stream.of(e3, e4);

        El r1 = stream1.findFirst().get();
        El r2 = stream2.findFirst().get();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r1, r2);
    }

    public static void testStreamFromArray2() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();

        El[] array1 = new El[] {e1, e2}; // 2 + 1 allocated in passingArrayElement
        El[] array2 = new El[] {e3, e4}; // 2 + 3 in array1

        Stream<El> stream1 = Stream.of(array1);
        Stream<El> stream2 = Stream.of(array2);

        El r1 = stream1.findFirst().get();
        El r2 = stream2.findFirst().get();

        passingArrayElement(array1);
        copyArray(array1, array2);

        PTAAssert.sizeEquals(3, r1);
        PTAAssert.sizeEquals(5, r2);
    }

    static void passingArrayElement(El[] array) {
        El ex = new El();
        array[0] = ex;
    }

    static void copyArray(El[] array1, El[] array2) {
        System.arraycopy(array1, 0, array2, 1, 2);
    }
}

class El {
}