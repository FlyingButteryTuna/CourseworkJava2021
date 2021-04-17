package com.example.demo;
import org.springframework.boot.SpringApplication;
import services.Service1;

import java.util.Collections;


public class MainS1 {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Service1.class);
    app.setDefaultProperties(Collections.singletonMap("server.port", "8080"));
    app.run(args);
  }
}
