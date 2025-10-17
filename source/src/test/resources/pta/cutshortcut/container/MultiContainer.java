import java.util.*;

public class MultiContainer {
    public static void main(String[] args) {
        testMapKeySet();
        testMapEntrySet1();
        testMapEntrySet2();
    }
    static void testMapKeySet() {
        K k1 = new K();
        K k2 = new K();
        V v1 = new V();
        V v2 = new V();
        Set<K> s1 = new HashSet<K>();
        s1.add(k1);
        Iterator<K> iterator1 = s1.iterator();
        K r1 = iterator1.next(); //pt(r1) = k1, k2

        Map<K,V> map1 = new HashMap<K,V>();
        map1.put(k2,v2);
        Set<K> s2 = map1.keySet();
        Iterator<K> iterator2 = s2.iterator();
        K r2 = iterator2.next(); //pt(r2) = k1, k2

        s1 = s2;

        PTAAssert.sizeEquals(2, r1, r2);
    }

    static void testMapEntrySet1() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();

        TreeMap<K,V> treeMap1 = new TreeMap<K,V>();
        ArrayList<Map.Entry<K,V>> al1 = new ArrayList<Map.Entry<K,V>>();
        treeMap1.put(k1, v1);
        Set<Map.Entry<K,V>> set1 = treeMap1.entrySet();
        Iterator<Map.Entry<K,V>> iterator1 = set1.iterator();
        Map.Entry<K,V> entry1 = iterator1.next();
        K m1k = entry1.getKey();     // k1
        al1.addAll(set1);
        Map.Entry<K,V> alr1 = al1.get(0);
        alr1.setValue(v2);
        V m1v1 = alr1.getValue();    // v1 v2
        V m1v2 = treeMap1.get(k1);   // v1 v2

        TreeMap<K,V> treeMap2 = new TreeMap<K,V>();
        ArrayList<Map.Entry<K,V>> al2 = new ArrayList<Map.Entry<K,V>>();
        treeMap2.put(k3, v3);
        Set<Map.Entry<K,V>> set2 = treeMap2.entrySet();
        Iterator<Map.Entry<K,V>> iterator2 = set2.iterator();
        Map.Entry<K,V> entry2 = iterator2.next();
        K m2k = entry2.getKey();     // k3
        al2.addAll(set2);
        Map.Entry<K,V> alr2 = al2.get(0);
        alr2.setValue(v4);
        V m2v1 = alr2.getValue();    // v3 v4
        V m2v2 = treeMap2.get(k3);   // v3 v4

        PTAAssert.sizeEquals(1, m1k, m2k);
        PTAAssert.sizeEquals(2, m1v1, m1v2, m2v1, m2v2);
        PTAAssert.disjoint(m1v1, m2v1);
    }

    static void testMapEntrySet2() {
        K k1 = new K();
        K k2 = new K();
        K k3 = new K();
        K k4 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();
        V v5 = new V();

        TreeMap<K,V> treeMap1 = new TreeMap<K,V>();
        ArrayList<Map.Entry<K,V>> al1 = new ArrayList<Map.Entry<K,V>>();
        treeMap1.put(k1, v1);
        Set<Map.Entry<K,V>> set1 = treeMap1.entrySet();
        Iterator<Map.Entry<K,V>> iterator1 = set1.iterator();
        Map.Entry<K,V> entry1 = iterator1.next();
        K m1k = entry1.getKey();     // k1, k3
        al1.addAll(set1);
        Map.Entry<K,V> alr1 = al1.get(0);
        alr1.setValue(v2);
        V m1v1 = alr1.getValue();    // v1 ~ v5
        V m1v2 = treeMap1.get(k1);   // v1 ~ v5


        TreeMap<K,V> treeMap2 = new TreeMap<K,V>();
        ArrayList<Map.Entry<K,V>> al2 = new ArrayList<Map.Entry<K,V>>();
        treeMap2.put(k3, v3);
        Set<Map.Entry<K,V>> set2 = treeMap2.entrySet();
        Iterator<Map.Entry<K,V>> iterator2 = set2.iterator();
        Map.Entry<K,V> entry2 = iterator2.next();
        K m2k = entry2.getKey();     // k1, k3
        al2.addAll(set2);
        Map.Entry<K,V> alr2 = al2.get(0);
        alr2.setValue(v4);
        V m2v1 = alr2.getValue();    // v1 ~ v5
        V m2v2 = treeMap2.get(k3);   // v1 ~ v5


        ArrayList<Map.Entry<K,V>> al3 = new ArrayList<Map.Entry<K,V>>();
        al3.addAll(al1);
        al3.addAll(al2);
        Map.Entry<K,V> alr3 = al3.get(0);
        alr3.setValue(v5);
        V r = alr3.getValue();      // v1 ~ v5

        PTAAssert.sizeEquals(2, m1k, m2k);
        PTAAssert.sizeEquals(5, m1v1, m1v2, m2v1, m2v2, r);
    }
}

class K{

}
class V{

}
