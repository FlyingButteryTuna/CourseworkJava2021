package com.example.demo;
import org.springframework.web.util.UriComponentsBuilder;
import services.Service3;
import java.io.IOException;


public class MainS3 {

  public static void main(String[] args) throws IOException {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("http://localhost:8081/getScheduleJson")
            .queryParam("fileName", "newSchedule.json");
    String newSchedule = Service3.restTemplate.getForObject(builder.toUriString(), String.class);
    Service3.simulate(newSchedule);

    builder = UriComponentsBuilder.fromUriString("http://localhost:8081/getScheduleJsonByName")
            .queryParam("fileName", "thisFileDoesNotExists");
    System.out.println(Service3.restTemplate.getForObject(builder.toUriString(), String.class));
    builder = UriComponentsBuilder.fromUriString("http://localhost:8081/getScheduleJsonByName")
            .queryParam("fileName", "newSchedule.json");
    System.out.println(Service3.restTemplate.getForObject(builder.toUriString(), String.class));
  }
}
