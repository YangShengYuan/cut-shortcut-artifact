class ComplexFieldLoad {

    public static void main(String[] args) {
        testNestedLoad();
    }

    static void testNestedLoad() {
        A n1 = new A();
        Student s1 = new Student(n1);
        A r1 = s1.getStdName();

        A n2 = new A();
        Student s2 = new Student(n2);
        A r2 = s2.getStdName();

        Person p1 = new Person();
        A n3 = new A();
        p1.setName(n3);
        A r3 = p1.getName();

        Person p2 = new Person();
        A n4 = new A();
        p2.setName(n4);
        A r4 = p2.getName();

        PTAAssert.sizeEquals(1, r1, r2);
        //cannot be distinguished by 1-layer

        PTAAssert.sizeEquals(1, r3, r4);
        PTAAssert.disjoint(r3, r4);
    }
}

class Person {
    A name;

    void setName(A n) {
        this.name = n;
    }

    A getName() {
        A r = this.name;
        return r;
    }
}

class Student extends Person {

    Student(A stdn) {
        super.setName(stdn);
    }

    A getStdName() {
        A stdr = super.getName();
        return stdr;
    }
}

class A {

}
