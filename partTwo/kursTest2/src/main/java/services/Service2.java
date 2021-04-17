package services;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;


@SpringBootApplication
@RestController
public class Service2
{
  private static final RestTemplateBuilder builder = new RestTemplateBuilder();
  private static final RestTemplate restTemplate = builder.build();

  /*public static void generateJsonWithSchedule() throws IOException, InterruptedException {
    String schedule = restTemplate.getForObject("http://localhost:8080/scheduleGenerate", String.class);
    ObjectMapper mapper = new ObjectMapper();

    Scanner scanner = new Scanner(System.in);

    while (true){
      System.out.println("Enter a new ship? yes/no");
      String answer = scanner.nextLine();
      if (answer.equals("yes")) {
        Service1.Ship newShip;
        if ((newShip = readNewShip(scanner)) == null){
          System.out.println("invalid input");
          continue;
        }
        schedule.add(newShip);
      }
      else if (answer.equals("no")){
        break;
      }
    }
    Collections.sort(schedule);
    String pathSchedule = "schedule.json";
    String pathResult = "result.json";
    File file = new File(pathSchedule);
    JsonGenerator g = mapper.getFactory().createGenerator(new FileOutputStream(file));
    mapper.writeValue(g, schedule);

    ArrayList<Service1.Ship> results = Service3.simulate(pathSchedule);
    //printResults(pathResult, results);
  }*/

  @PostMapping(path = "/PrintResults")
  public static void printResultsPostEndpoint(@RequestBody String results) throws IOException {
    String path = "results.json";
    BufferedWriter writer = new BufferedWriter(new FileWriter(path));
    writer.write(results);
    writer.close();
  }

  @GetMapping("/getScheduleJson")
  public static String createJsonSchedule(@RequestParam String fileName) throws IOException, InterruptedException {
    ObjectMapper mapper = new ObjectMapper();
    String newSchedule = restTemplate.getForObject("http://localhost:8080/scheduleGenerate", String.class);

    ArrayList<Service1.Ship> ships = mapper.readValue(newSchedule, new TypeReference<ArrayList<Service1.Ship>>(){});
    Scanner scanner = new Scanner(System.in);
    while (true){
      System.out.println("Enter a new ship? yes/no");
      String answer = scanner.nextLine();
      if (answer.equals("yes")) {
        Service1.Ship newShip;
        if ((newShip = readNewShip(scanner)) == null){
          System.out.println("invalid input");
          continue;
        }
        ships.add(newShip);
      }
      else if (answer.equals("no")){
        break;
      }
    }
    Collections.sort(ships);
    File file = new File(fileName);
    JsonGenerator g = mapper.getFactory().createGenerator(new FileOutputStream(file));
    mapper.writeValue(g, ships);

    return mapper.writeValueAsString(ships);
  }

  @GetMapping("/getScheduleJsonByName")
  public static String getJsonSchedule(@RequestParam String fileName) throws IOException {
    File file = new File(fileName);
    if (!file.exists()){
      return "Error! File was not found.";
    }
    else{
      Path pathResults = Paths.get(fileName);
      return Files.readString(pathResults, StandardCharsets.US_ASCII);
    }
  }

  private static Service1.Ship readNewShip(Scanner scanner) throws InterruptedException {
    String name;
    int day;
    long arrivalTime;
    String cargoType;
    int cargoWeight;
    System.out.println("Enter ship's name");
    name = scanner.nextLine();

    System.out.println("Enter ship's cargo type");
    cargoType = scanner.nextLine();

    Service1.CargoTypes cargoTypeFinal = switch (cargoType) {
      case "bulk" -> Service1.CargoTypes.BULK;
      case "liquid" -> Service1.CargoTypes.LIQUID;
      case "container" -> Service1.CargoTypes.CONTAINER;
      default -> null;
    };
    if (cargoTypeFinal == null){
      return null;
    }

    System.out.println("Enter ship's arrival day");
    day = scanner.nextInt();
    if (day < 1 || day > 30){
      return null;
    }

    System.out.println("Enter ship's arrival time in minutes");
    arrivalTime = scanner.nextLong();

    if (arrivalTime < 0) {
      return null;
    }
    Thread.sleep(10);

    System.out.println(arrivalTime);
    System.out.println("Enter cargo weight/amount");
    cargoWeight = scanner.nextInt();
    if (cargoWeight <= 0){
      return null;
    }
    arrivalTime += (day - 1) * 24 * 60;
    long unloadingDelay = ThreadLocalRandom.current().nextInt(0, 1441);
    return new Service1.Ship(name, day, arrivalTime, cargoTypeFinal, cargoWeight, unloadingDelay);
  }
}
