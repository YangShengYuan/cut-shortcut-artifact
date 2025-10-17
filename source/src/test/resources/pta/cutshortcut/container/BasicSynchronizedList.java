import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BasicSynchronizedList {

    public static void main(String[] args) {
        testSynchronizedList();
    }

    public static void testSynchronizedList() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        ArrayList<A> al1 = new ArrayList<A>(); // a1 a2 a4
        al1.add(a1);
        ArrayList<A> al2 = new ArrayList<A>(); // a3 a2 a4 + list1
        al2.add(a3);

        List<A> list1 = Collections.synchronizedList(al1); // al1 + a2
        List<A> list2 = Collections.synchronizedList(al2); // al2 + a4 + list1 = a1 a2 a3 a4

        list1.add(a2);
        list2.add(a4);

        A r1 = al1.get(0);
        A r2 = al2.get(0);
        A r3 = list1.get(0);
        A r4 = list2.get(0);

        list2.addAll(list1);

        List<A> sub1 = list1.subList(0, 1);
        List<A> sub2 = list2.subList(0, 1);
        A r5 = sub1.iterator().next();
        A r6 = sub2.iterator().next();

        PTAAssert.sizeEquals(3, r1, r3, r5);
        PTAAssert.sizeEquals(4, r2, r4, r6);
    }
}

class A {}
