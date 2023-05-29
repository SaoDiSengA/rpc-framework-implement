package com.yt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloServiceImpl implements HelloService{

    private static final Logger logger = LoggerFactory.getLogger(HelloServiceImpl.class);
    @Override
    public String sayHello(HelloObject helloObject) {
        logger.info("接收到消息：{}", helloObject.getMessage());
        return "这是Hello的Impl1方法" + helloObject.getId();
    }
}
