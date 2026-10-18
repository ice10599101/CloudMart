package com.cloudmart.file;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFileStorage
@EnableScheduling
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class FileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
