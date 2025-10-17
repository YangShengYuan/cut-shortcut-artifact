import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class BasicUnmodifiableCollection {
    public static void main(String[] args) {
        testUnmodifiableCollection();
    }

    public static void testUnmodifiableCollection() {
        A a1 = new A();
        A a3 = new A();

        ArrayList<A> al1 = new ArrayList<A>();
        al1.add(a1);
        ArrayList<A> al2 = new ArrayList<A>();
        al2.add(a3);

        Collection<A> c1 = Collections.unmodifiableCollection(al1);
        Collection<A> c2 = Collections.unmodifiableCollection(al2);

        A r1 = al1.get(0);  // a1
        A r2 = al2.get(0);  // a2
        A r3 = c1.iterator().next();  // a1
        A r4 = c2.iterator().next();  // a2

        PTAAssert.sizeEquals(1, r1, r2);
        PTAAssert.sizeEquals(1, r3, r4);
    }
}

class A {

}
