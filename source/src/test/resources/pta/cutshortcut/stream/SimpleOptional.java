import java.util.Optional;

public class SimpleOptional {
    public static void main(String[] args) {
        testOptional1();
        testOptional2();
    }

    static void testOptional1() {
        E e1 = new E();
        E e2 = new E();
        E e3 = new E();

        Optional<E> op1 = Optional.empty();
        Optional<E> op2 = Optional.of(e1);

        E r1 = op1.orElse(e2);
        E r2 = op2.orElse(e3);

        PTAAssert.sizeEquals(2, r2);
        PTAAssert.sizeEquals(1, r1);
        PTAAssert.disjoint(r1, r2);
    }

    static void testOptional2() {
        E e1 = new E();
        E e2 = new E();

        Optional<E> op1 = Optional.ofNullable(e1);
        Optional<E> op2 = Optional.of(e2);

        E r1 = op1.filter(null).get();
        E r2 = op2.filter(null).orElse(null);

        PTAAssert.sizeEquals(1, r2);
        PTAAssert.sizeEquals(1, r1);
        PTAAssert.disjoint(r1, r2);
    }

}

class E {

}
