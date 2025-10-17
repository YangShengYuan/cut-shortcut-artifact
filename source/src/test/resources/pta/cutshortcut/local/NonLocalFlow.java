class NonLocalFlow {

    public static void main(String[] args) {
        testDistinguish();
        testMerge();
    }

    static void testDistinguish() {
        B b = new B();
        b.f = "b.f";
        A a = new A();
        String s1 = a.distinguishP0_BdotF("s1", b);
        String s2 = a.distinguishP0_BdotF("s2", b);
        PTAAssert.notEquals(s1, s2);
    }

    static void testMerge() {
        B b = new B();
        b.f = "b.f";
        A a = new A();
        String s1 = a.mergeP0_BdotF("s1", b);
        String s2 = a.mergeP0_BdotF("s2", b);
        PTAAssert.equals(s1, s2);
    }
}

class A {
    String distinguishP0_BdotF(String p0, B b) {
        String r1 = p0;
        if (b == null) {
            return r1; // r1 is local
        } else {
            String r2 = b.f;
            return r2; // r2 is non-local
        }
    }

    String mergeP0_BdotF(String p0, B b) {
        String r;
        if (b == null) {
            r = p0;
        } else {
            r = b.f;
        }
        return r;
    }
}

class B {
    String f;
}
