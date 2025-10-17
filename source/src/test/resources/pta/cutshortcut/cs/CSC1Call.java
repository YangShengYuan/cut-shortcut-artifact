import java.util.stream.Stream;

public class CSC1Call {
    public static void main(String[] args){
        testCSCWith1Call();
    }

    static void testCSCWith1Call() {
        S s1 = new S();
        S s2 = new S();

        S r1 = identity(s1);
        S r2 = identity(s2);

        Stream<S> stm1 = Stream.of(s1);
        Stream<S> stm2 = Stream.of(s2);

        S r3 = stm1.findFirst().orElse(null);
        S r4 = stm2.findFirst().orElse(null);

        PTAAssert.sizeEquals(1, r1, r2);
        PTAAssert.sizeEquals(1, r3, r4);
    }

    // note that local-flow pattern should be turn-off.
    static S identity(S e) {
        return e;

    }
}

class S{
}
