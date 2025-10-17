public class MultiLayerLocal {
    public static void main(String[] args) {
        testMultiLayerLocal();
    }

    static void testMultiLayerLocal() {
        X x1 = new X();
        X x2 = new X();
        X r1 = foo1(x1);
        X r2 = foo2(x2);
    }

    static X foo1(X p) {
        X re1 = foo2(p) // re1 = p
        return re1;
    }

    static X foo2(X p2) {
        X re2 = foo3(p2); //  re2 = p2
        return re2;
    }

    static X foo3(X p3) {
        return p3;
    }

}

class X {

}
