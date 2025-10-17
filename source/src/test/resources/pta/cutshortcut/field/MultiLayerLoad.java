class MultiLayerLoad {

    public static void main(String[] args) {
        N n1 = new N();
        Evn evn1 = new AEvn();
        Std std1 = new Std();
        setNameOuter(std1, n1);
        N r1 = getNameOuter(evn1, std1);

        N n2 = new N();
        Evn evn2 = new BEvn();
        Std std2 = new Std();
        setNameOuter(std2, n2);
        N r2 = getNameOuter(evn2, std2);
    }

    static void setNameOuter(Std s, N n) {
        setNameInner(s, n);
    }

    static void setNameInner(Std s, N n) {
        s.name = n;
    }

    static N getNameOuter(Evn evn, Std s) {
        N re = evn.getNameInner(s); // AEvn.getName/ret -> re
                                    // BEvn.getName/ret -> re
        return re;
    }
}

class Std {
    N name;
}

class Evn {
    N getNameInner(Std s) {
        return null;
    }
}

class AEvn extends Evn {
    @Override
    N getNameInner(Std s) {
        return s.name;
    }
}

class BEvn extends Evn {
    @Override
    N getNameInner(Std s) {
        return new N();
    }
}

class N {

}