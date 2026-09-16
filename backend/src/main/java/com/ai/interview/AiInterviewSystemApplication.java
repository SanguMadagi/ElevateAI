package com.ai.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiInterviewSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiInterviewSystemApplication.class, args);
	}

}
