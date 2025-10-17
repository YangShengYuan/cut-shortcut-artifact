public class ComplexFieldLoad2 {

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

        Object csnr1 = cs1.getCstdName();
        Object csnr2 = cs2.getCstdName();
        Object csgr1 = cs1.getCstdGender();
        Object csgr2 = cs2.getCstdGender();
        Object csidr1 = cs1.getId();
        Object csidr2 = cs2.getId();
        Object csemailr1 = cs1.getEmail();
        Object csemailr2 = cs2.getEmail();

        Object hsnr1 = hs1.getHstdName();
        Object hsnr2 = hs2.getHstdName();
        Object hsgr1 = hs1.getHstdGender();
        Object hsgr2 = hs2.getHstdGender();
        Object hsidr1 = hs1.getId();
        Object hsidr2 = hs2.getId();
        Object hsgrader1 = hs1.getGrade();
        Object hsgrader2 = hs2.getGrade();

        Object snr1 = s1.getStdName();
        Object snr2 = s2.getStdName();
        Object sgr1 = s1.getStdGender();
        Object sgr2 = s2.getStdGender();
        Object sidr1 = s1.getId();
        Object sidr2 = s2.getId();

        Object pnr1 = p1.getName();
        Object pnr2 = p2.getName();
        Object pgr1 = p1.getGender();
        Object pgr2 = p2.getGender();

        PTAAssert.sizeEquals(1, pnr1, pnr2);
        PTAAssert.sizeEquals(8, snr1, snr2, csnr1, csnr2, hsnr1, hsnr2);
        PTAAssert.sizeEquals(1, csidr1, csidr2, hsidr1, hsidr2, sidr1, sidr2);
        PTAAssert.sizeEquals(1, csemailr1, csemailr2);
        PTAAssert.sizeEquals(3, hsgrader1, hsgrader2);
        PTAAssert.sizeEquals(1, pgr1, pgr2);
        PTAAssert.sizeEquals(7, sgr1, sgr2, csgr1, csgr2, hsgr1, hsgr2);
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

    String getName() {
        String N = this.name;
        return N;
    }

    String getGender() {
        String G = this.gender;
        return G;
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

    String getId() {
        String ID =  this.id;
        return ID;
    }

    String getStdName() {
        String stdN = super.getName();
        stdN = "bomb";
        return stdN;
    }

    String getStdGender() {
        String stdG = super.getGender();
        return stdG;
    }
}

class CollegeStudent extends Student {
    String email;

    CollegeStudent(String cstdn, String cgender, String cid, String email) {
        super(cstdn, cgender); // propstore cstdn
        super.setId("111111"); // can not propstore
        this.setEmail(email);  // propstore
    }

    void setEmail(String emailx) {
        this.email = emailx;   // cutStore
    }

    String getEmail() {
        String Email = this.email;
        return Email;
    }

    String getCstdName() {
        String CstdN = super.getStdName();
        return CstdN;
    }

    String getCstdGender() {
        String CstdG = super.getStdGender();
        return CstdG;
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

    void setGrade(String gradeX) {
        this.grade = gradeX;    // cutStore
    }


    String getGrade() {
        String Grade = this.grade;
        Grade = "bomb2";
        return Grade;
    }

    String getHstdName() {
        String HstdN = super.getStdName();
        return HstdN;
    }

    String getHstdGender() {
        String HstdG = super.getStdGender();
        return HstdG;
    }
}
