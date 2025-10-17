import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapClone {

    public static void main(String[] args) {
        testMapClone();
    }
    public static void testMapClone() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();

        MyClonnableMap<K, V> map1 = new MyClonnableMap<K, V>();
        MyClonnableMap<K, V> map2 = new MyClonnableMap<K, V>();
        map1.put(k1, v1);
        map2.put(k2, v2);

        MyClonnableMap<K, V> copy1 = (MyClonnableMap<K, V>) map1.clone();
        MyClonnableMap<K, V> copy2 = (MyClonnableMap<K, V>) map2.clone();
        copy1.put(k3, v3);
        copy2.put(k4, v4);

        V r1 = map1.get(k1);  // v1, v3
        V r2 = map2.get(k1);  // v2, v4
        V r3 = copy1.get(k1); // v1, v3
        V r4 = copy2.get(k2); // v2, v4

        PTAAssert.sizeEquals(2, r1, r2);
        PTAAssert.sizeEquals(2, r3, r4);
        PTAAssert.disjoint(r1, r2);
        PTAAssert.disjoint(r3, r4);
    }
}

class MyClonnableMap<K,V> implements Map<K,V>, Cloneable{

    private Map<K,V> innerMap = new HashMap<K,V>();

    public MyClonnableMap(Map<K,V> inner) {
        innerMap = inner;
    }

    public MyClonnableMap() {}

    @Override
    public int size() {
        return innerMap.size();
    }

    @Override
    public Object clone()  {
        return new MyClonnableMap<K,V>(this);
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
        return innerMap.containsValue(value);
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

class K {}

class V {}
