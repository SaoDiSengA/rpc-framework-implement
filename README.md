# 一、RPC是什么？

RPC（Remote Procedure Call Protocol）远程过程调用协议，它是一种通过网络从远程计算机程序上请求服务，而不需要了解底层网络技术的协议。简言之，RPC使得程序能够像访问本地系统资源一样，去访问远端系统资源。比较关键的一些方面包括：通讯协议、序列化、资源（接口）描述、服务框架、性能、语言支持等。

RPC是一种远程过程调用协议，而我们的RPC框架，例如Dubbo，就是封装了这个协议然后进行实现的。

一个RPC框架要进行使用应该要具有如下的组件（功能）
![img](https://img-blog.csdnimg.cn/14c8fe4aedaf464eb209626f733e928a.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA56eN5LiA5qO15qmZ5a2Q5qCR,size_20,color_FFFFFF,t_70,g_se,x_16)

首先从整体层次来看，一个RPC协议的框架应该具有三个层面。分别是

==服务的注册中心，请求服务的客户端，提供服务的服务端。==

简而言之，就是服务端需要把提供的服务注册到注册中心，而客户端可以发现注册中心的服务，它调用服务端的服务的时候，相当于调用它自己本地的方法，客户端是有服务的接口的，然而服务的实现类，只在服务端拥有。所以客户端的请求会通过网络传输去服务端调用接口和实现类，然后将执行的结果再返回给客户端。

关于这三个层面，其实细分的话，又可以分为以下几个部分，每一部分完成各自的任务。

1.客户端（上面提到了，客户端发起请求，调用远程方法）

2.客户端存根（存放服务端地址信息，将客户端的请求参数数据信息打包成网络消息，再通过网络传输发送给服务端）作为一个代理类。

3.网络传输 通过网络传输，把我们调用的远程接口中的参数传输给服务端，这样服务端的接口实现类才能进行处理，在处理完成之后，还要通过网络传输的方式把返回的结果发送回来。网络传输一般有原生的Soket方式，还有现在常用的Netty。

4.服务端 提供服务的一方，有远程接口和实现类。

5.服务端存根 接收客户端发送过来的请求消息并进行解析，然后再调用服务端的方法进行处理

这样的步骤中存在许多相关的问题，如下

1、如何确定客户端和服务端之间的通信协议？

2、如何更高效地进行网络通信？

3、服务端提供的服务如何暴露给客户端？

4、客户端如何发现这些暴露的服务？

5、如何更高效地对请求对象和响应结果进行序列化和反序列化操作？

这些问题，我们在后续完成整个框架的过程中会进行解答，首先我跟着声哥的步骤一样，我们默认客户端已经直到服务端的地址，那我们首先就只需要安排一下客户端和服务端的接口，以及服务端独有的实现类就行了。

# 二、RPC调用的简单实现

首先 搭建不同的模块，由于这里暂时只作为测试使用，我把接口的实现类写进rpc-server模块中，而客户端调用的接口和接口处理数据的实体类写到了rpc-api模块中。这里搭建的模块是Maven工程模块。

![](https://raw.githubusercontent.com/SaoDiSengA/Image/master/img/20230512112715.png)

然后我们正式编写代码

首先是接口

```java
public interface HelloService {
    String sayHello(HelloObject helloObject);
    //sayHello方法，参数是HelloObject对象
}
```

接口操作数据的实体类

 注意啦，在网络传输的过程中，实体类都需要实现Serializable接口，代表可序列化，

序列化作用：

    提供一种简单又可扩展的对象保存恢复机制。
    对于远程调用，能方便对对象进行编码和解码，就像实现对象直接传输。
    可以将对象持久化到介质中，就像实现对象直接存储。
    允许对象自定义外部存储的格式。
更多详细关于Serizlizable的内容，大家可以去这篇文章中详细阅读。

[谈谈实现Serializable接口的作用和必要性 - 简书](https://www.jianshu.com/p/4935a87976e5)

```java
package com.yt;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HelloObject {
    private Integer id;
    private String message;
}
```

接口的实现类

这里为了成功引入HelloService接口，需要在rpc-server模块的pom.xml文件中引入rpc-api模块

```java
        <dependency>
            <groupId>org.example</groupId>
            <artifactId>rpc-api</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>compile</scope>
        </dependency>
```

```java
public class HelloServiceImpl implements HelloService{
    @Override
    public String sayHello(HelloObject helloObject) {
        System.out.println(helloObject.getMessage());
        return "这是Hello的Impl1方法";
    }
}
```

# 三、传输的规则

我们直到，客户端有了服务的接口，服务端有了服务的实现类。。。那我们可以想象一下，在一次请求中，服务端需要哪些信息才能够确定它要去调用哪个实现类呢？

首先肯定要知道接口的名字，以及调用的接口中的方法名，另外再确定方法的参数值和参数类型，这样就可以确保服务端能够唯一确认一个实现类中的方法进行处理。

所以我们可以模拟一个我们自己写的传输规则，也就是我们的PRC请求过程中，我们的请求是遵循这个格式的。

由于请求格式这些东西肯定是通用的模块，所以我们再建立一个rpc-common模块，用于存放实体对象、工具类等公用类。

这里使用了Lombok的@Data注解和@Builder注解，@Data注解自动生成getter和setter，toString方法，而@Builder注解帮我们使用了建造者模式，有兴趣的可以去设计模式中了解，简单来说就是可以让我们通过链式编程的方式在创建对象的时候进行实例化。

```java
@Data
@Builder
public class RpcRequest implements Serializable {
    /**
     * 待调用接口名称
     */
    private String interfaceName;
    /**
     * 待调用方法名称
     */
    private String methodName;
    /**
     * 调用方法的参数
     */
    private Object[] parameters;
    /**
     * 调用方法的参数类型
     */
    private Class<?>[] paramTypes;
}
```

有了RPC的Request，那我们服务的过程中不可能只有请求，我们还会有响应，所以我们还需要有一个RpcResponse，用于封装响应的信息。

```java
package com.yt;

import lombok.Data;

import java.io.Serializable;

@Data
public class RpcResponse<T> implements Serializable {
    /**
     * 响应状态码
     */
    private Integer statusCode;
    /**
     * 响应状态补充信息
     */
    private String message;
    /**
     * 响应数据
     */
    private T data;

    public static <T> RpcResponse<T> success(T data) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setStatusCode(ResponseCode.SUCCESS.getCode());
        response.setData(data);
        return response;
    }
    public static <T> RpcResponse<T> fail(ResponseCode code) {
        RpcResponse<T> response = new RpcResponse<>();
        response.setStatusCode(code.getCode());
        response.setMessage(code.getMessage());
        return response;
    }
}
```

既然要用到返回的Code，那我们就再定义一个枚举类。

```java
package com.yt;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCode {
 
    SUCCESS(200, "调用方法成功"),
    FAIL(500, "调用方法失败"),
    METHOD_NOT_FOUND(500, "未找到指定方法"),
    CLASS_NOT_FOUND(500, "未找到指定类");
 
    private final int code;
    private final String message;
 
}
```

# 四、客户端实现——动态代理

那么，我们在拥有了客户端的接口，以及服务端的实现类，并且我们自定义了服务端如何匹配对应的实体类后，我们应该思考，由于在客户端这一侧我们并没有接口的具体实现类，就没有办法直接生成实例对象。这时，我们可以通过动态代理的方式生成实例，并且调用方法时生成需要的RpcRequest对象并且发送给服务端。

这里我们采用JDK动态代理，代理类是需要实现`InvocationHandler`接口的。

```java
package com.yt;

import com.yt.entity.RpcRequest;
import com.yt.entity.RpcResponse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RpcClientProxy implements InvocationHandler {
    
    private String host;
    private int port;
 
    public RpcClientProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcRequest rpcRequest = RpcRequest.builder()
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .parameters(args)
                .paramTypes(method.getParameterTypes())
                .build();
        RpcClient rpcClient = new RpcClient();
        return ((RpcResponse) rpcClient.sendRequest(rpcRequest, host, port)).getData();
    }
}
```

这里生成代理对象的参数，host和port分别对应主机的ip地址和端口号，因为我们需要通过地址和端口号才能找到服务端主机并且去使用里面的服务，使用getProxy()方法来生成代理对象。

InvocationHandler接口需要实现invoke()方法，来指明代理对象的方法被调用时的动作。在这里，我们显然就需要生成一个RpcRequest对象，发送出去，然后返回从服务端接收到的结果即可

在这里，生成了RpcRequest对象后，我们使用一个RpcClient来发送这个请求，并且通过getData方法来获取响应的数据。

```java
package com.yt;

import com.yt.entity.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class RpcClient {
 
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);
 
    public Object sendRequest(RpcRequest rpcRequest, String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            objectOutputStream.writeObject(rpcRequest);
            objectOutputStream.flush();
            return objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("调用时有错误发生：", e);
            return null;
        }
    }
}
```

这里的实现方式就是直接使用Java的序列化方式（通过实现Serizlizable），创建一个Socket，利用Socket进行传输，获取ObjectOutputStream对象，然后把需要发送的对象传进去即可，接收时获取ObjectInputStream对象，readObject()方法就可以获得一个返回的对象。 

# 五、服务端的实现——反射调用

在我们前面完成了远程调用的接口，实现类，远程调用封装的对线，传输规则等等，最后就只需要完成服务端进行功能实现就可以实现一个简单的远程调用了，这里服务端是通过反射来进行调用的

主要流程就是使用一个ServerSocket监听某个端口，循环接收连接请求，如果发来了请求就创建一个线程，在新线程中处理调用。这里创建线程采用线程池的方式。

然后在RpcServer里面对外提供一个接口的调用服务，添加register方法，在注册完一个服务后立刻开始监听。

```java
package com.yt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class RpcServer {
 
    private final ExecutorService threadPool;
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
 
    public RpcServer() {
        int corePoolSize = 5;
        int maximumPoolSize = 50;
        long keepAliveTime = 60;
        BlockingQueue<Runnable> workingQueue = new ArrayBlockingQueue<>(100);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workingQueue, threadFactory);
    }


    public void register(Object service, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("服务器正在启动...");
            Socket socket;
            while((socket = serverSocket.accept()) != null) {
                logger.info("客户端连接！Ip为：" + socket.getInetAddress());
                threadPool.execute(new WorkerThread(socket, service));
            }
        } catch (IOException e) {
            logger.error("连接时有错误发生：", e);
        }
    }
}
```

这里向工作线程WorkerThread传入了socket和用于服务端实例service。

WorkerThread实现了Runnable接口，用于接收RpcRequest对象，解析并且调用，生成RpcResponse对象并传输回去。run方法如下：

```java
package com.yt;

import com.yt.entity.RpcRequest;
import com.yt.entity.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

public class WorkerThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
    Socket socket;
    Object service;
    public WorkerThread(Socket socket, Object service) {
        this.socket = socket;
        this.service = service;
    }

    @Override
    public void run() {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
            RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();
            Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
            Object returnObject = method.invoke(service, rpcRequest.getParameters());
            objectOutputStream.writeObject(RpcResponse.success(returnObject));
            objectOutputStream.flush();
        } catch (IOException | ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("调用或发送时有错误发生：", e);
        }
    }
}
```

其中，通过class.getMethod方法，传入方法名和方法参数类型即可获得Method对象。如果你上面RpcRequest中使用String数组来存储方法参数类型的话，这里你就需要通过反射生成对应的Class数组了。通过method.invoke方法，传入对象实例和参数，即可调用并且获得返回值。

# 六、测试

我们已经在上面已经实现了一个HelloService的实现类了，现在我们只需要创建一个RpcServer并且把这个实现类注册进去就行了

```java
package com.yt;

public class TestServer {
    public static void main(String[] args) {
        HelloService helloService = new HelloServiceImpl();
        RpcServer rpcServer = new RpcServer();
        rpcServer.register(helloService, 9000);
    }
}
```

服务端开放在9000端口。

客户端方面，我们需要通过动态代理，生成代理对象，并且调用，动态代理会自动帮我们向服务端发送请求的

```java
package com.yt;

public class TestClient {
    public static void main(String[] args) {
        RpcClientProxy proxy = new RpcClientProxy("127.0.0.1", 9000);
        HelloService helloService = proxy.getProxy(HelloService.class);
        HelloObject object = new HelloObject(12, "This is a message");
        String res = helloService.sayHello(object);
        System.out.println(res);
    }
}
```

 创建一个HelloObject对象来作为传递的参数，然后启动服务端，再启动客户端。

    服务端输出
    
    服务器正在启动...
    
    客户端连接！Ip为：127.0.0.1
    
    接收到：This is a message
    
    客户端输出
    
    这是调用的返回值，id=12 
七、总结

最后，总结一下这次测试的RPC全过程。

1.首先，客户端接收到请求，然后以调用本地方法的方式调用远程服务。

2.客户端根接收到调用后，通过代理对象，将方法，参数等信息封装成能够在网络中传输的消息体（RpcRequest）要记得实现序列化接口。

3.客户端找到远程服务的地址，将消息体（RpcRequest）发送给服务端根。

4.服务端根进行反序列化操作，把消息体转换成RpcRequest对象，并且根据转换成的RpcRequest对象中的参数（方法名，实现类名，方法参数值，参数类型）等等去调用服务端的方法。

5.服务端进行方法的业务逻辑处理，在处理完毕后，返回处理结果（RpcResponse对象）组装成能在网络传输的消息体给服务端根。

6.服务端根再把处理结果进行序列化。发送给客户端。

7.客户端根接收到消息体，进行反序列化操作，变回RpcResponse对象，然后给客户端去进行处理

流程大家可以看下图

![img](https://img-blog.csdnimg.cn/5e75a96935ba437b8524d732c4e15ef5.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA56eN5LiA5qO15qmZ5a2Q5qCR,size_20,color_FFFFFF,t_70,g_se,x_16)