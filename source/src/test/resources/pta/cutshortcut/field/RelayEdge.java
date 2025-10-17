class RelayEdge {
    public static void main(String[] args) {
        testSimpleRelay();
    }

    static void testSimpleRelay() {
        N n1 = new N();
        Stud s1 = new Stud(n1);
        N r1 = s1.getName();

        N n2 = new N();
        Stud s2 = new Stud(n2);
        N r2 = s2.getName();
    }
}

class Stud {

    N name;

    Stud(N n) {
        this.name = n;
    }

    N getName() {
        N ret;
        if (this.name == null) {
            N innerN = new N();
            System.out.println(innerN);
            ret = innerN;
        } else {
            ret = this.name;
        }
        return ret;
    }
}

class N {

}