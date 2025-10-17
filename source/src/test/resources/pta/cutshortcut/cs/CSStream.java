import java.util.stream.Stream;

public class CSStream {
    public static void main(String[] args) {
        testStreamCS1();
    }

    static void testStreamCS1() {
        B b1 = new B();
        B b2 = new B();

        // 1-call is able to distinguish
        Stream<B> stm1 = byPipeline(b1); // callee context [13]
        Stream<B> stm2 = byPipeline(b2); // callee context [14]

        B r1 = stm1.findFirst().orElse(null);
        B r2 = stm2.findFirst().orElse(null);

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(1, r1, r2);
    }

    static Stream<B> byPipeline(B b) {
        Stream<B> stm = Stream.of(b);
        // generate 2 L
        // [13]:L and [14]:L
        return stm;
    }
}

class B {}