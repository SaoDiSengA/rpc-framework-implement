package com.yt;

public class TestClient {
    public static void main(String[] args) {
        RpcClientProxy proxy = new RpcClientProxy("127.0.0.1", 9000);
        System.out.println("==========");
        HelloService helloService = proxy.getProxy(HelloService.class);
        System.out.println("==========");
        HelloObject object = new HelloObject(12, "This is a message");
        System.out.println("==========");
        String res = helloService.sayHello(object);   // invoke方法在这里进行调用
        System.out.println("==========");
//        String res1 = helloService.sayHello(object);   // invoke方法在这里进行调用
        System.out.println(res);
        System.out.println("==========");

    }
}