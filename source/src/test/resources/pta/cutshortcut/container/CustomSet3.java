import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class CustomSet3 {
    public static void main(String[] args) {
        testCustomSet();
    }

    public static void testCustomSet() {
        A a1 = new A();
        MySetX<A> al1 = new MySetX<A>();
        al1.add(a1);
        Iterator<A> itr1 = al1.iterator(); // itr1 is taint
        A r1 = itr1.next();                // undo cut

        A a2 = new A();
        MySetX<A> al2 = new MySetX<A>();
        al2.add(a2);
        Iterator<A> itr2 = al2.iterator();
        A r2 = itr2.next();

        A a3 = new A();
        MySetX<A> al3 = new MySetX<A>();
        al3.add(a3);
        Iterator<A> itr3 = al3.fun();
        A r3 = itr3.next();

        PTAAssert.sizeEquals(3, r3);
    }
}

class MySetX<E> extends HashSet<E> {
    Set inner = new HashSet<E>();

    @Override
    public boolean add(E e) {
        return inner.add(e);
    }

    public Iterator<E> fun () {
        return inner.iterator();
    }
}

class A {

}
