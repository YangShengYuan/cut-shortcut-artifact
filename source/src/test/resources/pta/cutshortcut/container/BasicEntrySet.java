import java.util.*;

public class BasicEntrySet {
    public static void main(String[] args) {
        testEntrySet();
        testSetEntry();
        testSingleEntry();
        testCustomEntry();
    }
    public static void testEntrySet() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();

        HashMap<K, V> hashMap1 = new HashMap<K, V>(); // host L20
        HashMap<K, V> hashMap2 = new HashMap<K, V>(); // host L21

        hashMap1.put(k1, v1);
        hashMap1.put(k2, v2);
        hashMap2.put(k3, v3);
        hashMap2.put(k4, v4);

        Set<Map.Entry<K, V>> entrySet1 =  hashMap1.entrySet();
        Set<Map.Entry<K, V>> entrySet2 =  hashMap2.entrySet();

        ArrayList<Object> al1 = new ArrayList<Object>(entrySet1);
        ArrayList<Object> al2 = new ArrayList<Object>(entrySet2);

        Object r1 = al1.get(0); // <k1,v1> <k2,v2>
        Object r2 = al2.get(0); // <k3,v3> <k4,v4>

        Map.Entry<K,V> entry1 = (Map.Entry<K, V>) r1;
        K r3 = entry1.getKey();  // k1, k2
        V r4 = entry1.getValue();// v1, v2

        PTAAssert.sizeEquals(2, r3, r4);
    }

    public static void testSetEntry() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();

        HashMap<K, V> hashMap1 = new HashMap<K, V>();
        hashMap1.put(k1, v1);
        hashMap1.put(k2, v2);
        Set<Map.Entry<K, V>> entrySet1 =  hashMap1.entrySet();
        ArrayList<Object> al1 = new ArrayList<Object>(entrySet1);

        Object r1 = al1.get(0);
        Map.Entry<K,V> entry1 = (Map.Entry<K, V>) r1;
        entry1.setValue(v3);

        K r2 = hashMap1.keySet().iterator().next(); // k1, k2
        V r3 = hashMap1.get(k1); // v1, v2, v3

        PTAAssert.sizeEquals(2, r2);
        PTAAssert.sizeEquals(3, r3);
    }

    public static void testSingleEntry() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();
        Object ox = new Object();
        V v5 = new V();

        TreeMap<K, V> treeMap1 = new TreeMap<K, V>();
        TreeMap<K, V> treeMap2 = new TreeMap<K, V>();
        treeMap1.put(k1, v1);
        treeMap1.put(k2, v2);
        treeMap2.put(k3, v3);
        treeMap2.put(k4, v4);

        Map.Entry<K, V> entry1 = treeMap1.lowerEntry(k1);
        Map.Entry<K, V> entry2 = treeMap2.ceilingEntry(k3);

        K r1 = entry1.getKey();  // k1, k2
        V r2 = entry2.getValue();// v3, v4

        ArrayList<Object> al1 = new ArrayList<Object>();
        al1.add(entry1);
        al1.add(ox);
        Map.Entry<K, V> entry3 = (Map.Entry<K, V>) al1.get(0);
        Object r3 = entry3.getKey();
        Object r4 = entry3.getValue();

        entry3.setValue(v5);
        V r5 = entry1.getValue(); // v1, v2, v5

        PTAAssert.sizeEquals(2, r1, r3, r2);
        PTAAssert.sizeEquals(3, r4, r5);
    }

    public static void testCustomEntry() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();
        HashMap<K, V> hashMap1 = new HashMap<K, V>();
        HashMap<K, V> hashMap2 = new HashMap<K, V>();
        hashMap1.put(k1, v1);
        hashMap2.put(k2, v2);

        Set<Map.Entry<K, V>> entrySet1 =  hashMap1.entrySet();
        ArrayList<Object> al1 = new ArrayList<Object>(entrySet1);

        Set<Map.Entry<K, V>> entrySet2 =  hashMap2.entrySet();
        ArrayList<Object> al2 = new ArrayList<Object>(entrySet2);

        MyEntry<K, V> myEntry1 = new MyEntry<K, V>(k3, v3);
        MyEntry<K, V> myEntry2 = new MyEntry<K, V>(k4, v4);

        al1.add(myEntry1);
        al2.add(myEntry2);

        Object r1 = al1.get(0);
        Map.Entry<K, V> entry = (Map.Entry<K, V>) r1;
        V r2 = entry.getValue();

        PTAAssert.contains(r1, myEntry1);
    }
}

class K {}

class V {}

class MyEntry<K, V> implements Map.Entry<K, V> {
    K key;
    V value;

    MyEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        this.value = value;
        return this.value;
    }

    public void setV(V value) {
        this.value = value;
    }
}

