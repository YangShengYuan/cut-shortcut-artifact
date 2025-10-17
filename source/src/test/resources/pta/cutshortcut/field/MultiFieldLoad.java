public class MultiFieldLoad {
    public static void main(String[] args) {
        Inner i1 = new Inner();
        Inner i2 = new Inner();
        Inner i3 = new Inner();
        Outer o1 = new Outer();
        Outer o2 = new Outer();
        Outer o3 = new Outer();
        o1.i = i1;
        o2.i = i2;
        o3.i = i3;
        Inner r1 = loadField1(o1, o2, o3, 0);
        // r1 <- i1, [loadField1/new Inner], [fun/new Inner], i3, i6, i2, i5

        Inner i4 = new Inner();
        Inner i5 = new Inner();
        Inner i6 = new Inner();
        Outer o4 = new Outer();
        Outer o5 = new Outer();
        Outer o6 = new Outer();
        o4.i = i4;
        o5.i = i5;
        o6.i = i6;
        Inner r2 = loadField1(o4, o5, o6, 2);
        // r1 <- i4, [loadField1/new Inner], [fun/new Inner], i3, i6, i2, i5

        PTAAssert.sizeEquals(7, r1, r2);
    }

    public static Inner loadField1(Outer o1, Outer o2, Outer o3, int con) {
        Inner temp1;
        Inner temp2 = new Inner();
        if (con == 1) {
            return o2.i;  // load info (ret1, o2.i)
        } else if (con == 2) {
            temp1 = o1.i; // load info (ret2, o1.i)
            Inner tempx = temp1;
            Inner tempy = o2.i; // load info (ret2, o2.i)
            tempx = tempy;
            return tempx;
        } else if (con == 3) {
            temp2 = o2.i; // X
            return temp2;
        } else {
            Inner temp3 = fun();
            temp3 = o3.i; // X
            return temp3;
        }
    }

    public static Inner fun() {
        return new Inner();
    }

}

class Outer {
    Inner i;
}

class Inner {

}
