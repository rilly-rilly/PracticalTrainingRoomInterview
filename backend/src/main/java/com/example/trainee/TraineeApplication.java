package com.example.trainee;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.trainee.mapper")
public class TraineeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraineeApplication.class, args);
    }
}
