import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BasicSynchronizedMap {
    public static void main(String[] args) {
        testSynchronizedMap();
    }

    public static void testSynchronizedMap() {
        K k1 = new K();
        K k2 = new K();
        V v1 = new V();
        V v2 = new V();

        Map<K, V> map1 = new HashMap<K, V>();
        map1.put(k1, v1);

        Map<K, V> synMap = Collections.synchronizedMap(map1);
        synMap.put(k2, v2);

        K r1 = synMap.keySet().iterator().next();
        V r2 = synMap.get(r1);

        PTAAssert.sizeEquals(2, r1, r2);
    }
}

class K {

}

class V {

}