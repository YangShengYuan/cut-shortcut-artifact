class ComplexFieldStore2 {

    public static void main(String[] args) {
        testThreeLayerStore();
    }

    static void testThreeLayerStore() {
        CollegeStudent cs1 = new CollegeStudent("csn1", "csg1", "csid1", "csemail1");
        CollegeStudent cs2 = new CollegeStudent("csn2", "csg2", "csid2", "csemail2");

        HighschoolStudent hs1 = new HighschoolStudent("hsn1", "hsg1", "hsid1", "hsgrade1");
        HighschoolStudent hs2 = new HighschoolStudent("hsn2", "hsg2", "hsid2", "hsgrade2");

        Student s1 = new Student("sn1", "sg1");
        s1.setId("sid1");
        Student s2 = new Student("sn2", "sg2");
        s2.setId("sid2");

        Person p1 = new Person();
        p1.setName("pn1");
        p1.setGender("pg1");
        Person p2 = new Person();
        p2.setName("pn2");
        p2.setGender("pg2");

        PTAAssert.sizeEquals(1, cs1.name, cs2.name);
        PTAAssert.disjoint(cs1.name, cs2.name);
        PTAAssert.sizeEquals(7, cs1.gender, cs2.gender);
        PTAAssert.equals(cs1.gender, cs2.gender);
        PTAAssert.sizeEquals(1, cs1.id, cs2.id);
        PTAAssert.equals(cs1.id, cs2.id);
        PTAAssert.sizeEquals(1, cs1.email, cs2.email);
        PTAAssert.disjoint(cs1.email, cs2.email);
        //cs1.name = {"csn1"} cs2.name = {"csn2"}
        //cs1.gender = cs2.gender = {"sg1", "sg2", "helicopter", "hsg1", "hsg2", "csg1", "csg2"}
        //cs1.id = {"111111"} cs2.id = {"111111"}
        //cs1.email = {"csemail1"} cs2.email = {"csemail2"}

        PTAAssert.sizeEquals(3, hs1.name, hs2.name);
        PTAAssert.equals(hs1.name, hs2.name);
        PTAAssert.sizeEquals(7, hs1.gender, hs2.gender);
        PTAAssert.equals(hs1.gender, hs2.gender);
        PTAAssert.sizeEquals(1, hs1.id, hs2.id);
        PTAAssert.disjoint(hs1.id, hs2.id);
        PTAAssert.sizeEquals(1, hs1.grade, hs2.grade);
        PTAAssert.disjoint(hs1.grade, hs2.grade);
        //hs1.name = hs2.name = {"highschool", "hsn1", "hsn2"}
        //hs1.gender = hs2.gender = {"sg1", "sg2", "helicopter", "hsg1", "hsg2", "csg1", "csg2"}
        //hs1.id = {"hsid1"} hs2.id = {"hsid2"}
        //hs1.grade = {"hsgrade1"} hs2.grade = {"hsgrade2"}

        PTAAssert.sizeEquals(1, s1.name, s2.name);
        PTAAssert.disjoint(s1.name, s2.name);
        PTAAssert.sizeEquals(7, s1.gender, s2.gender);
        PTAAssert.equals(s1.gender, s2.gender);
        PTAAssert.sizeEquals(1, s1.id, s2.id);
        PTAAssert.disjoint(s1.id, s2.id);
        //s1.name = {"sn1"} s2.name = {"sn2"}
        //s1.gender = s2.name = {"sg1", "sg2", "helicopter", "hsg1", "hsg2", "csg1", "csg2"}
        //s1.id = {"sid1"} s2.id = {"sid2"}

        PTAAssert.sizeEquals(1, p1.name, p2.name);
        PTAAssert.disjoint(p1.name, p2.name);
        PTAAssert.sizeEquals(1, p1.gender, p2.gender);
        PTAAssert.disjoint(p1.gender, p2.gender);
        //p1.name = {"pn1"} p2.name = {"pn2"}
        //p1.gender = {"pg1"} p2.gender = {"pg2"}
    }
}

class Person {
    String name;

    String gender;

    void setName(String n) {
        this.name = n;  // cutStore
    }

    void setGender(String g) {
        this.gender = g; // cutStore
    }
}

class Student extends Person {
    String id;

    Student(String stdn, String sgender) {
        super.setName(stdn);     // propstore
        sgender = "helicopter";
        super.setGender(sgender);// can not propstore for sgender
    }

    void setId(String id) {
        this.id = id;           // cutStore
    }
}

class CollegeStudent extends Student {
    String email;

    CollegeStudent(String cstdn, String cgender, String cid, String email) {
        super(cstdn, cgender); // propstore cstdn
        super.setId("111111"); // can not propstore
        this.setEmail(email);  // propstore
    }

    void setEmail(String email) {
        this.email = email;   // cutStore
    }
}

class HighschoolStudent extends Student {
    String grade;

    HighschoolStudent(String hstdn, String hgender, String hid, String grade) {
        super(hstdn, hgender); // can not propstore for hstdn
        super.setId(hid);      // propstore
        this.setGrade(grade);  // propstore
        hstdn = "highschool";
    }

    void setGrade(String grade) {
        this.grade = grade;    // cutStore
    }
}
