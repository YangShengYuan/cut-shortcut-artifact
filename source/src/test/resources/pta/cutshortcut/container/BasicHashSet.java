import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BasicHashSet {
    public static void main(String[] args) {
        testSet();
        testSetCollectivelyAdd();
        testClone();
    }

    static void testSet() {
        Set<A> set1 = new HashSet<A>();
        Set<A> set2 = new HashSet<A>();
        A a1 = new A();
        A a2 = new A();
        set1.add(a1);
        set2.add(a2);
        Iterator<A> itr1 = set1.iterator();
        Iterator<A> itr2 = set2.iterator();
        A r1 = itr1.next(); //pt(r1) = o1,o3
        A r2 = itr2.next(); //pt(r2) = o2,o4
        A a3 = new A();
        A a4 = new A();
        set1.add(a3);
        set2.add(a4);

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testSetCollectivelyAdd() {
        Set<A> set1 = new HashSet<A>();
        Set<A> set2 = new HashSet<A>();
        Set<A> set3 = new HashSet<A>(set2);
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();
        set1.add(a1);
        set1.add(a2);
        set2.add(a3);
        set3.add(a4);
        set2.addAll(set1);
        A r1 = set1.iterator().next();//pt(r1) = {o1,o2}
        A r2 = set2.iterator().next();//pt(r2) = {o1,o2,o3}
        A r3 = set3.iterator().next();//pt(r3) = {o1,o2,o3,o4}

        PTAAssert.sizeEquals(2, r1);
        PTAAssert.sizeEquals(3, r2);
        PTAAssert.sizeEquals(4, r3);
        PTAAssert.contains(r3, r2, r1);
    }

    static void testClone() {
        HashSet<A> set1 = new HashSet<A>();
        A a1 = new A();
        A a2 = new A();
        set1.add(a1);
        set1.add(a2);
        Object set2 = set1.clone();
        HashSet<A> set3 = (HashSet<A>) set2;
        A r1 = set3.iterator().next();
    }
}

class A {

}
