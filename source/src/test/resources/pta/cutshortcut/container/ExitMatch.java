import java.util.Stack;

public class ExitMatch {
    public static void main(String[] args) {
        testMyStack();
    }

    public static void testMyStack() {
        A a1 = new A();
        A a2 = new A();
        A a3 = new A();
        A a4 = new A();

        MyStack<A> myStack1 = new MyStack<A>();
        MyStack<A> myStack2 = new MyStack<A>();

        myStack1.add(a1);
        myStack1.add(a2);
        myStack2.add(a3);
        myStack2.add(a4);

        A r1 = myStack1.get(0); // a1, a2, a3, a4
        A r2 = myStack2.get(0); // a3, a2, a3, a4

        PTAAssert.sizeEquals(4, r1, r2);
    }

}

class MyStack<E> extends Stack<E> {
    public boolean add(E e) {
        return super.add(e);
    }
    //no explicit exit. hidden superclass exit.
}

class A {}
