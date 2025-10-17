import java.util.concurrent.*;

public class ConcurrentContainers {
    public static void main(String[] args) {
        ConcurrentLinkedQueue<A> concurrentLinkedQueue = new ConcurrentLinkedQueue<A>();
        ConcurrentSkipListSet<A> concurrentSkipListSet = new ConcurrentSkipListSet<A>();
        CopyOnWriteArrayList<A> copyOnWriteArrayList = new CopyOnWriteArrayList<A>();
        CopyOnWriteArraySet<A> copyOnWriteArraySet = new CopyOnWriteArraySet<A>();
        LinkedBlockingDeque<A> linkedBlockingDeque = new LinkedBlockingDeque<A>();
        LinkedBlockingQueue<A> linkedBlockingQueue = new LinkedBlockingQueue<A>();
        PriorityBlockingQueue<A> priorityBlockingQueue = new PriorityBlockingQueue<A>();
        SynchronousQueue<A> synchronousQueue = new SynchronousQueue<A>();
        ArrayBlockingQueue<A> arrayBlockingQueue = new ArrayBlockingQueue<A>(1);
        DelayQueue<A> delayQueue = new DelayQueue<A>();
        ConcurrentSkipListMap<K, V> concurrentSkipListMap = new ConcurrentSkipListMap<K, V>();
    }
}

class A implements Delayed{
    @Override
    public long getDelay(TimeUnit unit) {
        return 0;
    }

    @Override
    public int compareTo(Delayed o) {
        return 0;
    }
}

class K {

}

class V {

}
