import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomMap {
    public static void main(String[] args) {
        testCustomMap();
    }

    static void testCustomMap() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        K k5 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();
        V v5 = new V();

        MyMap<K,V> myMap1 = new MyMap<K,V>();
        myMap1.put(k1,v1);

        HashMap<K,V> map1 = new HashMap<K,V>();
        map1.put(k2,v2);
        map1.putAll(myMap1);

        HashMap<K,V> map2 = new HashMap<K,V>();
        map2.put(k3,v3);

        HashMap<K,V> map3 = new HashMap<K,V>(map1);
        map3.put(k4,v4);

        MyMap<K,V> myMap2 = new MyMap<K,V>();
        myMap2.put(k5,v5);

        V r1 = map1.get(k1); // --> v1, v2, v3, v4, v5
        V r2 = map2.get(k2); // --> v3
        V r3 = map3.get(k3); // --> v1, v2, v3, v4, v5
        V r4 = myMap1.get(k1);// --> v1, v5

        PTAAssert.sizeEquals(5, r1, r3);
        PTAAssert.sizeEquals(1, r2);
        PTAAssert.sizeEquals(2, r4);
    }
}

class MyMap<K,V> implements Map<K,V> {

    private final HashMap<K,V> innerMap = new HashMap<K,V>();

    @Override
    public int size() {
        return innerMap.size();
    }

    @Override
    public boolean isEmpty() {
        return innerMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return innerMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return innerMap.containsKey(value);
    }

    @Override
    public V get(Object key) {
        return innerMap.get(key);
    }

    @Override
    public V put(K key, V value) {
        return innerMap.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return innerMap.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        innerMap.putAll(m);
    }

    @Override
    public void clear() {
        innerMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return innerMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return innerMap.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return innerMap.entrySet();
    }
}

class K {

}

class V {

}
