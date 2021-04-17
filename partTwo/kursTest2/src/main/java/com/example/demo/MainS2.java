package com.example.demo;
import org.springframework.boot.SpringApplication;
import services.Service2;
import java.util.Collections;


public class MainS2 {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Service2.class);
    app.setDefaultProperties(Collections.singletonMap("server.port", "8081"));
    app.run(args);
  }
}
