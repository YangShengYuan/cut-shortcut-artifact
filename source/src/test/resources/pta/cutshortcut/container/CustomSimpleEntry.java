import java.util.AbstractMap;
public class CustomSimpleEntry {
    public static void main(String[] args) {
        K k1 = new K();
        K k2 = new K();
        V v1 = new V();
        V v2 = new V();
        V v3 = new V();
        V v4 = new V();

        MySimpleEntry<K, V> entry1 = new MySimpleEntry<K, V>(k1, v1);
        MySimpleEntry<K, V> entry2 = new MySimpleEntry<K, V>(k2, v2);

        entry1.mySetValue(v3);
        entry1.setValue(v4);

        V r1 = entry1.getValue();
        V r2 = entry2.getValue();

        PTAAssert.contains(r1, v3, v4);
        PTAAssert.contains(r2, v3, v4);
    }
}

class MySimpleEntry<K,V> extends AbstractMap.SimpleEntry<K, V> {
    public MySimpleEntry(K key, V value) {
        super(key, value);
    }

    public void mySetValue(V value) {
        super.setValue(value);
    }

    public V getValue() {
        return super.getValue();
    }
}

class K {
}

class V {
}