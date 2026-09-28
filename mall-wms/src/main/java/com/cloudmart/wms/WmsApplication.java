package com.cloudmart.wms;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan({"com.cloudmart.wms.repository", "com.cloudmart.common.async.mapper"})
@Import(GlobalExceptionHandler.class)
public class WmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
