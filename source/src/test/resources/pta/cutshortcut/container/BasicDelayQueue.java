import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class BasicDelayQueue {
    public static void main(String[] args) {
        testDelayQueue();
    }
    public static void testDelayQueue() {
        E e1 = new E();
        E e2 = new E();
        E e3 = new E();
        E e4 = new E();
        DelayQueue<E> queue1 = new DelayQueue<E>();
        DelayQueue<E> queue2 = new DelayQueue<E>();
        queue1.add(e1);
        queue1.offer(e2);
        queue2.put(e3);
        queue2.add(e4);

        E r1 = queue1.peek();
        E r2 = queue2.poll();

        PTAAssert.disjoint(r1, r2);
        PTAAssert.sizeEquals(2, r2, r1);
    }
}

class E implements Delayed {

    @Override
    public long getDelay(TimeUnit unit) {
        return 0;
    }

    @Override
    public int compareTo(Delayed o) {
        return 0;
    }
}
