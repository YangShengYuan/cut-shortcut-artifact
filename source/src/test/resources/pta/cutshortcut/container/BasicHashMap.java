import java.util.*;

class BasicHashMap {
    public static void main(String[] args) {
        testMap();
        testCollectivelyAdd();
    }
    static void testMap() {
        Map<K, V> map1 = new HashMap<K,V>(); //[host:MAP1]
        Map<K, V> map2 = new HashMap<K,V>(); //[host:MAP2]

        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();

        map1.put(k1,v1);
        map1.put(k2,v2);//[host:MAP1] -> {k:k1,k2; v:v1,v2}, entrance
        map2.put(k3,v3);
        map2.put(k4,v4);//[host:MAP2] -> {k:k3,k4; v:v3,v4}, entrance

        V r1 = map1.get(k1); // pt(r1) = {v1,v2}
        V r2 = map2.get(k2); // pt(r2) = {v2,v3,v4}

        Set<K> kSet1 = map1.keySet();//[host:MAP1]
        Iterator<K> itr1 = kSet1.iterator();//[host:MAP1]
        K r3 = itr1.next();//[host:MAP1] -> {k1, k2}

        Set<K> kSet2 = map2.keySet();
        Iterator<K> itr2 = kSet2.iterator();
        K r4 = itr2.next(); // {k3, k4}

        Collection<V> vSet1 = map1.values();
        Iterator<V> itr3 = vSet1.iterator();
        V r5 = itr3.next(); // {v1, v2}

        Collection<V> vSet2 = map2.values();
        Iterator<V> itr4 = vSet2.iterator();
        V r6 = itr4.next(); // {v2, v3, v4}

        Set<Map.Entry<K,V>> eSet1 = map1.entrySet(); //[host:MAP1.ALL]
        Iterator<Map.Entry<K,V>> entryIterator1 = eSet1.iterator();//[host:MAP1.ALL]
        Map.Entry<K,V> entry1 = entryIterator1.next(); // [host:MAP1.ALL], no exit here.
        K r7 = entry1.getKey(); // [host:MAP1.ALL] // {k1, k2}

        Set<Map.Entry<K,V>> eSet2 = map2.entrySet();
        Iterator<Map.Entry<K,V>> entryIterator2 = eSet2.iterator();
        Map.Entry<K,V> entry2 = entryIterator2.next();
        entry2.setValue(v2); // [host:MAP2.ALL] entrance }

        PTAAssert.sizeEquals(3, r2, r6);
        PTAAssert.sizeEquals(2, r1, r3, r4, r5, r7);
    }

    static void testCollectivelyAdd() {
        Map<K, V> map1 = new HashMap<K,V>();

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

        map1.put(k1,v1);

        Map<K, V> map2 = new HashMap<K,V>(map1);
        map2.put(k2,v2);

        Map<K, V> map3 = new HashMap<K,V>(map2);
        map3.put(k3,v3);

        Map<K, V> map4 = new HashMap<K,V>();
        map4.put(k4,v4);

        Map<K, V> map5 = new HashMap<K,V>();
        map5.putAll(map3);
        map5.putAll(map4);

        //map1 -> map2 -> map3 -> map5
        //map4 -> map5

        K m1k = map1.keySet().iterator().next(); // k1
        K m2k = map2.keySet().iterator().next(); // k1, k2
        K m3k = map3.keySet().iterator().next(); // k1, k2, k3
        K m4k = map4.keySet().iterator().next(); // k4
        K m5k = map5.keySet().iterator().next(); // k1, k2, k3, k4

        V m1v = map1.values().iterator().next(); // v1
        V m2v = map2.values().iterator().next(); // v1, v2
        V m3v = map3.values().iterator().next(); // v1, v2, v3
        V m4v = map4.values().iterator().next(); // v4
        V m5v = map5.values().iterator().next(); // v1, v2, v3, v4

        PTAAssert.sizeEquals(1, m1k, m1v, m4k, m4v);
        PTAAssert.sizeEquals(2, m2k, m2v);
        PTAAssert.sizeEquals(3, m3k, m3v);
        PTAAssert.sizeEquals(4, m5k, m5v);
        PTAAssert.contains(m5k, m1k, m2k, m3k);
        PTAAssert.contains(m5v, m1v, m2v, m3v);
    }
}

class K {

}

class V {

}
