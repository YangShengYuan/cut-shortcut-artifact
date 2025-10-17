import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapArrayEntrance {
    public static void main(String[] args) {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();
        Object[] array1 = {k1, k2, v1, v2};
        Object[] array2 = {k3, k4, v3, v4};

        ArrayMap arrayMap1 = new ArrayMap(array1);
        ArrayMap arrayMap2 = new ArrayMap(array2);

        Object r1 = arrayMap1.get(k1);
        V r2 = arrayMap2.get(k2);
    }
}

class ArrayMap implements Map<K, V> {
    private Map<K, V> innerMap = new HashMap<K, V>();

    public ArrayMap(Object[] array) {
        innerMap.put((K) array[0], (V) array[0]);
    }

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

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}

class K {

}

class V {

}
