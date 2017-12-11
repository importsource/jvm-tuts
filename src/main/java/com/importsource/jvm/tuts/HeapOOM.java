package com.importsource.jvm.tuts;

import java.util.ArrayList;
import java.util.List;

/**
 * -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
 * @author hezhuofan
 */
public class HeapOOM {

    static class OOMObject{}

    public static void main(String[] args) {
        List<OOMObject> list=new ArrayList<>();
        while(true){
            list.add(new OOMObject());
        }
    }
}
