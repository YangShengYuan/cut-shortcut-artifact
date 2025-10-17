import java.util.Iterator;
import java.util.stream.Stream;

public class StreamIterator {
    public static void main(String[] args) {
        testStreamItr();
    }

    static void testStreamItr() {
        El e1 = new El();
        El e2 = new El();

        Stream<El> stream1 = Stream.of(e1);
        Iterator<El> itr1 = stream1.iterator();

        Stream<El> stream2 = Stream.of(e2);
        Iterator<El> itr2 = stream2.iterator();

        El r1 = itr1.next();
        El r2 = itr2.next();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
    }
}

class El {

}