import java.util.stream.Stream;

public class StreamConcat {
    public static void main(String[] args) {
        testConcat1();
        testConcat2();
    }

    static void testConcat1() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);
        Stream<El> stream3 = Stream.of(e3);
        Stream<El> stream4 = Stream.of(e4);

        Stream<El> conceited1 = Stream.concat(stream1, stream2);
        Stream<El> conceited2 = Stream.concat(stream3, stream4);

        El r1 = conceited1.findFirst().get();
        El r2 = conceited2.findFirst().get();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r1, r2);
    }

    static void testConcat2() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Stream<El> stream2 = Stream.of(e2);
        Stream<El> stream3 = Stream.of(e3);
        Stream<El> stream4 = Stream.of(e4);
        Stream<El> conceited1 = Stream.concat(stream1, stream2);
        conceited1 = Stream.concat(stream1, stream3);
        stream3 = stream4;

        El r1 = conceited1.findFirst().get();

        PTAAssert.sizeEquals(4, r1);
    }
}

class El {
}