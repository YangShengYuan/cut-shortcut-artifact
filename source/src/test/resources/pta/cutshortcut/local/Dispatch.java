class Dispatch {
    public static void main(String[] args) {
        A a = new A();
        A aOrB = args == null ? new A() : new B();
        Object o1 = a.getA();
        Object o2 = aOrB.getA();
        PTAAssert.sizeEquals(1, o1);
        PTAAssert.sizeEquals(2, o2);
        PTAAssert.disjoint(o1, o2);
    }
}

class A {
    A getA() {
        return this;
    }
}

class B extends A {
    A getA() {
        return new A();
    }
}
