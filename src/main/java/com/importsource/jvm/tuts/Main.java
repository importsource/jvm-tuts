package com.importsource.jvm.tuts;

class Main {
    public static void main(String[] args) {
        example();
    }
    public static void example() {
        Foo foo = new Foo(); //alloc
        Bar1 bar = new Bar1(); //alloc
        bar.setFoo(foo);
    }
}

class Foo {}

class Bar1 {
    private Foo foo;
    public void setFoo(Foo foo) {
        this.foo = foo;
    }
}
