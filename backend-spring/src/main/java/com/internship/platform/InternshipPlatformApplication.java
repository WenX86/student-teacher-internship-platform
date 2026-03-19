package com.internship.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.internship.platform.mapper")
@SpringBootApplication
public class InternshipPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(InternshipPlatformApplication.class, args);
    }
}
