package com.importsource.jvm.tuts;

/**
 * @author hezhuofan
 */
public class HandlePromotionFailure {
    private static final int _1MB = 1024 * 1024;

    /**
     * VM参数：-Xms20M -Xmx20M -Xmn10M -XX:+PrintGCDetails -XX:SurvivorRatio=8 -XX:-HandlePromotionFailure
     */
    public static void testHandlePromotion() {
        System.out.println((1024L*3L)*100/8192L);
        byte[] allocation1, allocation2, allocation3, allocation4;
        allocation1 = new byte[2 * _1MB];
        allocation2 = new byte[2 * _1MB];
        allocation3 = new byte[2 * _1MB];
        allocation4 = new byte[4 * _1MB];
    }

    public static void main(String[] args) {
        testHandlePromotion();
    }
}
