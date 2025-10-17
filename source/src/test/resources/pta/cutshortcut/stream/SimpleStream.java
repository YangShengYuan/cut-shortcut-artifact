import java.util.stream.Stream;

public class SimpleStream {
    public static void main(String[] args) {
        testSimpleStream1();
        testSimpleStream2();
        testSimpleStream3();
        testSimpleStream4();
    }

    static void testSimpleStream1() {
        El e1 = new El();
        El e2 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);

        Stream<El> stream3 = stream1.sequential();

        El r1 = stream3.findFirst().get();
        El r2 = stream2.findFirst().get();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
    }

    static void testSimpleStream2() {
        El e1 = new El();
        El e2 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);

        Stream<El> stream3 = stream1.sorted();

        El r1 = stream3.findFirst().get();
        El r2 = stream2.findFirst().get();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
    }

    static void testSimpleStream3() {
        El e1 = new El();
        El e2 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);

        Stream<El> stream3 = stream1.unordered().limit(10).skip(10);

        // orElseThrow is unsound in ci, related to Exception Analysis?
        El r1 = stream3.findAny().orElseThrow(null);
        El r2 = stream2.findAny().orElseGet(null);

        PTAAssert.sizeEquals(1, r2);
    }

    static void testSimpleStream4() {
        El e1 = new El();
        El e2 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);

        Stream<El> stream3 = stream1.parallel().onClose(null);

        // orElseThrow is unsound in ci, related to Exception Analysis?
        El r1 = (stream3.min(null)).orElseThrow(null);
        El r2 = stream2.max(null).orElseGet(null);

        PTAAssert.sizeEquals(1, r2);
    }
}

class El {

}