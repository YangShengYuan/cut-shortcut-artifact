import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BasicArrayBlockingQueue {
    public static void main(String[] args) {
        try {
            testQueue();
            testIterator();
            testCollectivelyOut();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static void testQueue() throws InterruptedException {
        ArrayBlockingQueue<A> q1 = new ArrayBlockingQueue<A>(10);
        ArrayBlockingQueue<A> q2 = new ArrayBlockingQueue<A>(10);
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q1.offer(a2);
        q2.put(a3);
        q2.offer(a4,10, TimeUnit.DAYS);
        A r1 = q1.poll(); // o1, o2
        A r2 = q2.peek(); // o3, o4

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testIterator(){
        ArrayBlockingQueue<A> q1 = new ArrayBlockingQueue<A>(10);
        ArrayBlockingQueue<A> q2 = new ArrayBlockingQueue<A>(10);
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q1.offer(a2);
        q2.add(a3);
        q2.offer(a4);
        Iterator<A> itr1 = q1.iterator();
        A r1 = itr1.next(); // pt(r1) = {o1, o2}
        Iterator<A> itr2 = q2.iterator();
        A r2 = itr2.next(); // pt(r2) = {o3, o4}

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testCollectivelyOut(){
        ArrayBlockingQueue<A> q1 = new ArrayBlockingQueue<A>(10);
        ArrayBlockingQueue<A> q2 = new ArrayBlockingQueue<A>(10);
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.offer(a1);
        q1.add(a2);
        q2.add(a3);
        q2.offer(a4);
        ArrayList<A> al1 = new ArrayList<A>();
        ArrayList<A> al2 = new ArrayList<A>();
        q1.drainTo(al1);
        q2.drainTo(al2);
        A r1 = al1.get(0); // o1, o2
        A r2 = al2.get(0); // o3, o4
        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }
}

class A {

}
