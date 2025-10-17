import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BasicSynchronizedCollection {

    public static void main(String[] args) {
        testSynchronizedCollection();
    }

    public static void testSynchronizedCollection() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        ArrayList<A> al1 = new ArrayList<A>();
        al1.add(a1);
        ArrayList<A> al2 = new ArrayList<A>();
        al2.add(a3);

        Collection<A> list1 = Collections.synchronizedCollection(al1);
        Collection<A> list2 = Collections.synchronizedCollection(al2);

        list1.add(a2);
        list2.add(a4);

        A r1 = al1.get(0); // a1, a2, a4
        A r2 = al2.get(0); // a3, a2, a4
        A r3 = list1.iterator().next(); // a1, a2, a4
        A r4 = list2.iterator().next(); // a3, a2, a4

        PTAAssert.sizeEquals(3, r1, r2);
        PTAAssert.sizeEquals(3, r3, r4);
    }
}

class A {}
