class ComplexFieldStore {

    public static void main(String[] args) {
        testNestedStore();
        testRedefineStore1();
        testRedefineStore2();
        testRedefineStore3();
    }

    static void testNestedStore() {
        Student s1 = new Student("name1");
        Student s2 = new Student("name2");

        Person p1 = new Person();
        p1.setName("name3");
        Person p2 = new Person();
        p2.setName("name4");

        PTAAssert.sizeEquals(1, s1.name, s2.name);
        PTAAssert.disjoint(s1.name, s2.name);

        PTAAssert.sizeEquals(1, p1.name, p2.name);
        PTAAssert.disjoint(p1.name, p2.name);
    }

    static void testRedefineStore1() {
        Student s1 = new Student();
        s1.setName1("n1");

        Student s2 = new Student();
        s2.setName1("n2");

        PTAAssert.sizeEquals(2, s1.name, s2.name);
        PTAAssert.notEquals(s1.name, s2.name);
        //pts(s1.name) = {"n1", "x"};
        //pts(s2.name) = {"n2", "x"};
    }

    static void testRedefineStore2() {
        Student s1 = new Student();
        s1.setName2("n1", "n2");

        Student s2 = new Student();
        s2.setName2("n3", "n4");

        PTAAssert.sizeEquals(2, s1.name, s2.name);
        PTAAssert.disjoint(s1.name, s2.name);
    }

    static void testRedefineStore3() {
        Student s1 = new Student();
        s1.setName3("n1", "n2");

        Student s2 = new Student();
        s2.setName3("n3", "n4");

        PTAAssert.sizeEquals(3, s1.name, s2.name);
        PTAAssert.notEquals(s1.name, s2.name);
        //pts(s1.name) = {"n1", "n2", "n4"};
        //pts(s2.name) = {"n3", "n2", "n4"};
    }
}

class Person {
    String name;

    void setName(String n) {
        this.name = n;
    }
}

class Student extends Person {

    Student(String stdn) {
        super.setName(stdn);
    }

    Student() {
    }

    void setName1(String n) {
        this.name = n;  //cutStore
        this.name = "x";
    }

    void setName2(String n, String s) {
        this.name = n;  //cutstore
        this.name = s;  //cutStore
    }

    void setName3(String n, String s) {
        this.name = n;   // cutStore
        String temp = s;
        this.name = temp;// not cutStore
    }
}
