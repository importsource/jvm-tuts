package com.importsource.jvm.tuts;

/**
 * java -XX:+UnlockDiagnosticVMOptions
 *      -XX:+PrintAssembly
 *      -Xcomp
 *      -XX:CompileCommand=dontinline,*Bar.sum
 *      -XX:CompileCommand=compileonly,*Bar.sum
 *
 *
 * PATH LD_LIBRARY_PATH=/Users/hezhuofan/Downloads/hsdis
 * @author hezhuofan
 */
public class Bar {
    int a=1;
    static int b=2;
    public int sum(int c){
        return a+b+c;
    }

    public static void main(String[] args){
        new Bar().sum(3);
    }
}
