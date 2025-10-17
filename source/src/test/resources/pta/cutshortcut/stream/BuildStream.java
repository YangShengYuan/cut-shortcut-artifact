import java.util.stream.Stream;

public class BuildStream {
    public static void main(String[] args) {
        testBuilder1();
        testBuilder2();
        testBuilder3();
    }

    static void testBuilder1() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();
        El e5 = new El();
        El e6 = new El();

        Stream.Builder<El> builder1 = Stream.builder(); // L1
        builder1.add(e1);
        builder1.add(e2).accept(e3);

        Stream.Builder<El> builder2 = Stream.builder(); // L2
        builder2.add(e4);
        builder2.add(e5).accept(e6);

        Stream<El> stream1 = builder1.build(); // L1
        Stream<El> stream2 = builder2.build(); // L2

        El r1 = stream1.findFirst().get();
        El r2 = stream2.findFirst().get();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(3, r1, r2);
    }

    static void testBuilder2() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();
        El e5 = new El();
        El e6 = new El();

        Stream.Builder<El> builder1 = Stream.builder(); // L1
        Stream.Builder<El> builder2 = builder1.add(e1); // L1 <- e1
        builder1.add(e2).accept(e3); // L1 <- e2, e3

        builder2.add(e4); // L1 <- e4
        builder2.add(e5).accept(e6); // L1 <- e5, e6

        Stream<El> stream1 = builder1.build();
        Stream<El> stream2 = builder2.build();

        El r1 = stream1.findFirst().get();
        El r2 = stream2.findFirst().get();

        PTAAssert.sizeEquals(6, r1, r2);
    }

    static void testBuilder3() {
        El e1 = new El();
        El e2 = new El();
        El e3 = new El();
        El e4 = new El();
        El e5 = new El();
        El e6 = new El();

        Stream.Builder<El> builder1 = Stream.builder(); // L1
        builder1.add(e1); // L1 <- e1
        builder1.add(e2).accept(e3); // L1 <- e2, e3

        Stream.Builder<El> builder2 = Stream.builder(); // L2
        builder2.add(e4); // L2 <- e4
        builder2.add(e5).accept(e6); // L2 <- e5, e6

        Stream<El> stm1 = builder1.build(); // L1, L2
        builder1 = builder2; // builder1 also contains L2 now.

        Stream<El> stm2 = builder2.build(); // L2

        El r1 = stm1.findFirst().get();
        El r2 = stm2.findFirst().get();

        PTAAssert.sizeEquals(6, r1);
        PTAAssert.sizeEquals(6, r2);
    }
}

class El{
}
