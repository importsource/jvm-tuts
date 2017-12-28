package com.importsource.clz;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author hezhuofan
 */
public class ClassLoaderTest {
    public static void main(String[] args) {
        ClassLoader myLoader=new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                try {
                String fileName=name.substring(name.lastIndexOf(".")+1)+".class";
                InputStream is=getClass().getResourceAsStream(fileName);
                if(is==null){
                    return super.loadClass(name);
                }
                byte[] b= new byte[0];
                    b = new byte[is.available()];
                    is.read(b);
                    return defineClass(name,b,0,b.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name);
                }
            }
        };
        Object obj= null;
        try {
            obj = myLoader.loadClass("com.importsource.clz.ClassLoaderTest").newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println(obj.getClass());
        System.out.println(obj instanceof com.importsource.clz.ClassLoaderTest);
    }

}
