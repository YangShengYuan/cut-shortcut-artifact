import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
public class BasicPriorityQueue {
    public static void main(String[] args) {
        testQueue();
        testIterator();
        testCollectivelyAdd();
    }
    static void testQueue() {
        Queue<A> q1 = new PriorityQueue<A>();
        Queue<A> q2 = new PriorityQueue<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q2.add(a3);
        A r1 = q1.element();//pt(r1) = {o1,o2}
        A r2 = q1.remove(); //pt(r2) = {o1,o2}
        A r3 = q2.poll();   //pt(r3) = {o3,o4}
        A r4 = q2.peek();   //pt(r4) = {o3,o4}
        q1.offer(a2);
        q2.offer(a4);

        PTAAssert.sizeEquals(2, r1, r2, r3, r4);
        PTAAssert.equals(r1, r2);
        PTAAssert.equals(r3, r4);
        PTAAssert.disjoint(r1, r3);
    }

    static void testIterator() {
        Queue<A> q1 = new PriorityQueue<A>();
        Queue<A> q2 = new PriorityQueue<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q1.add(a2);
        q2.add(a3);
        q2.add(a4);
        Iterator<A> itr1 = q1.iterator();
        A r1 = itr1.next(); // pt(r1) = {o1, o2}
        Iterator<A> itr2 = q2.iterator();
        A r2 = itr2.next(); // pt(r2) = {o3, o4}

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.disjoint(r1, r2);
    }

    static void testCollectivelyAdd() {
        Queue<A> q1 = new PriorityQueue<A>();
        Queue<A> q2 = new PriorityQueue<A>();
        A a1 = new A();//o1
        A a2 = new A();//o2
        A a3 = new A();//o3
        A a4 = new A();//o4
        q1.add(a1);
        q1.add(a2);
        q2.add(a3);
        q2.addAll(q1);
        Queue<A> q3 = new PriorityQueue<A>(q2);
        q3.add(a4);
        A r1 = q1.element(); //pt(r1) = {o1,o2}
        A r2 = q2.element(); //pt(r2) = {o1,o2,o3}
        A r3 = q3.element(); //pt(r3) = {o1,o2,o3,o4}

        PTAAssert.sizeEquals(2, r1);
        PTAAssert.sizeEquals(3, r2);
        PTAAssert.sizeEquals(4, r3);
        PTAAssert.contains(r3, r1, r2);
    }
}
class A {

}
