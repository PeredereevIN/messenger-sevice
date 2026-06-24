package com.peredereevin.messengerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class MessengerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessengerServiceApplication.class, args);
    }

}
