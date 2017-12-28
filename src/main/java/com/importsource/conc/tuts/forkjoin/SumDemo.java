package com.importsource.conc.tuts.forkjoin;

/**
 * @author hezhuofan
 */
public class SumDemo {

    public static void main(String[] args) throws Exception {
        // 创建随机数组成的数组:
        long[] array = new long[500000];
        fillRandom(array);
        Result result=new Computor(500,100).compute(array);
        System.out.println("Fork/join sum: " + result.getSum() + " in " + result.getDuration() + " ms.");
    }

    private static void fillRandom(long[] array) {
        for (int i = array.length-1; i >0; i--) {
            array[i]=i;
            System.out.println(array[i]);
        }
    }

}
