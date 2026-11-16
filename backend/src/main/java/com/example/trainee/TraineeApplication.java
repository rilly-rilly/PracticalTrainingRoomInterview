package com.example.trainee;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.trainee.mapper")
@EnableScheduling
public class TraineeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraineeApplication.class, args);
    }
}
