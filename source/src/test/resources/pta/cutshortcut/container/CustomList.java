import java.util.ArrayList;
import java.util.Iterator;

public class CustomList {
    public static void main(String[] args) {
        testCustomList();
    }

    public static void testCustomList() {
        O o1 = new O();
        O o2 = new O();

        MyList<O> l1 = new MyList<O>();
        l1.myAdd(o1);

        MyList<O> l2 = new MyList<O>();
        l2.myAdd(o2);

        // call super.exit() inside sub.myExit();
        O r1 = l1.myGet(0);
        O r2 = l2.myGet(0);

        // get super$iterator by sub.myTransfer();
        Object r3 = l1.myIterator().next();
        Object r4 = l2.myIterator().next();

        // get MyIterator by sub.myTransfer();
        MyItr<O> myItr1 = l1.myFakeIterator();
        MyItr<O> myItr2 = l2.myFakeIterator();
        O o3 = new O();
        O o4 = new O();
        myItr1.put(o3);
        myItr2.put(o4);
        Object r5 = myItr1.next();
        Object r6 = myItr2.next();

        // call super.exit()
        O r7 = l1.get(0);
        O r8 = l1.get(0);

        // get super$iterator by super.transfer();
        Object r9 = l1.iterator().next();
        Object r10 = l2.iterator().next();

        PTAAssert.sizeEquals(2, r1, r2, r3, r4, r7, r8, r9, r10);
        PTAAssert.sizeEquals(2, r5, r6);
        PTAAssert.disjoint(r1, r5);
    }
}

public class O {
}


public class MyList<E> extends ArrayList<E> {
    void myAdd(E e) {
        super.add(e);
    }

    E myGet(int i) {
        return super.get(i);
    }

    Iterator<E> myIterator() {
        return super.iterator();
    }

    MyItr<E> myFakeIterator() {
        return new MyItr<E>();
    }
}

public class MyItr<E> implements Iterator<E> {

    private E fake;

    public void put(E e) {
        this.fake = e;
    }

    public boolean hasNext() {
        return false;
    }

    public E next() {
        return fake;
    }

    public void remove() {

    }
}