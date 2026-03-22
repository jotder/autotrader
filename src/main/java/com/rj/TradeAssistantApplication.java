package com.rj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;

@SpringBootApplication(exclude = {HttpClientAutoConfiguration.class})
public class TradeAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeAssistantApplication.class, args);
    }
}
