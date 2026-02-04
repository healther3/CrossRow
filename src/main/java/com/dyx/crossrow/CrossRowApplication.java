package com.dyx.crossrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrossRowApplication {

    public static void main(String[] args) {
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "./config/gcp-key.json");
        SpringApplication.run(CrossRowApplication.class, args);
    }

}
