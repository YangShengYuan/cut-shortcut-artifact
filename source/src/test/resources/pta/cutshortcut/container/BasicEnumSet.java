import java.util.EnumSet;
import java.util.Collection;

public class BasicEnumSet {

    enum Size {
        SMALL, MEDIUM, LARGE, EXTRA
    }

    public static void main(String[] args) {
        testEnumSet();
    }

    public static void testEnumSet() {
        //allOf
        EnumSet<Size> sizes1 = EnumSet.allOf(Size.class);
        Size r1 = sizes1.iterator().next();

        //noneOf & add
        EnumSet<Size> sizes2 = EnumSet.noneOf(Size.class);
        sizes2.add(Size.SMALL);
        Size r2 = sizes2.iterator().next();

        //range
        EnumSet<Size> sizes3 = EnumSet.range(Size.MEDIUM, Size.EXTRA);
        Size r3 = sizes3.iterator().next();

        //addAll
        EnumSet<Size> sizes4 = EnumSet.noneOf(Size.class);
        sizes4.addAll(sizes1);
        Size r4 = sizes4.iterator().next();

        //of
        EnumSet<Size> sizes5 = EnumSet.of(Size.SMALL, Size.LARGE);
        Size r5 = sizes5.iterator().next();

        //copyOf
        EnumSet<Size> sizes6 = EnumSet.copyOf(sizes5);
        Size r6 = sizes6.iterator().next();

        //copyOf2
        Collection<Size> collection = sizes5;
        EnumSet<Size> sizes7 = EnumSet.copyOf(collection);
        Size r7 = sizes7.iterator().next();

        //complementOf
        EnumSet<Size> sizes8 = EnumSet.complementOf(sizes5);
        Size r8 = sizes8.iterator().next();
    }

}
