package services;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Service2
{
  public static void generateJsonWithSchedule() throws IOException, InterruptedException {
    ArrayList<Service1.Ship> schedule = Service1.GenerateSchedule();
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
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
    printResults(pathResult, results);
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

  private static void printResults(String path, ArrayList<Service1.Ship> stats) throws IOException {
    JsonFactory jfactory = new JsonFactory();
    JsonGenerator jGenerator = jfactory.createGenerator(new File(path), JsonEncoding.UTF8);
    long waitingSum = 0;
    int waitingAmount = 0;
    long unloadingDelaySum = 0;
    int unloadingDelayAmount = 0;
    long maxUnloadingDelay = -1;
    Collections.sort(stats);
    int unloadedUnfinishedCount = 0;
    for(Service1.Ship ship : stats){
      if (!ship.unloadFinished){
        unloadedUnfinishedCount++;
      }
      jGenerator.writeStartObject(); // {
      long arrivalTime = ship.arrivalTime + (ship.arrivalDay - 1) * 24L * 60;
      String fullArrivalTime = (TimeUnit.MINUTES.toDays(arrivalTime) + 1) +
              ":" +
              (TimeUnit.MINUTES.toHours(arrivalTime) - (TimeUnit.MINUTES.toDays(arrivalTime)) * 24) +
              ":" +
              (TimeUnit.MINUTES.toMinutes(arrivalTime) - (TimeUnit.MINUTES.toHours(arrivalTime) * 60));
      String fullWaitingTime = (TimeUnit.MINUTES.toDays(ship.waitingTime)) +
              ":" +
              (TimeUnit.MINUTES.toHours(ship.waitingTime) - (TimeUnit.MINUTES.toDays(ship.waitingTime) * 24)) +
              ":" +
              (TimeUnit.MINUTES.toMinutes(ship.waitingTime) - (TimeUnit.MINUTES.toHours(ship.waitingTime) * 60));
      String unloadingStart = (TimeUnit.MINUTES.toDays(ship.realArrivalTime) + 1) +
              ":" +
              (TimeUnit.MINUTES.toHours(ship.realArrivalTime) - (TimeUnit.MINUTES.toDays(ship.realArrivalTime)) * 24) +
              ":" +
              (TimeUnit.MINUTES.toMinutes(ship.realArrivalTime) - (TimeUnit.MINUTES.toHours(ship.realArrivalTime) * 60));

      String unloadingTime = (TimeUnit.MINUTES.toDays(ship.unloadingTime)) +
              ":" +
              (TimeUnit.MINUTES.toHours(ship.unloadingTime) - (TimeUnit.MINUTES.toDays(ship.unloadingTime)) * 24) +
              ":" +
              (TimeUnit.MINUTES.toMinutes(ship.unloadingTime) - (TimeUnit.MINUTES.toHours(ship.unloadingTime) * 60));
      String cargoTypeFinal = switch (ship.cargoType) {
        case BULK -> "bulk";
        case LIQUID -> "liquid";
        case CONTAINER -> "container";
      };
      jGenerator.writeStringField("Name", ship.name);
      jGenerator.writeStringField("Cargo Type", cargoTypeFinal);
      jGenerator.writeStringField("Arrival time" , fullArrivalTime);
      jGenerator.writeStringField("Waiting time", fullWaitingTime);
      jGenerator.writeStringField("Unloading start", unloadingStart);
      jGenerator.writeStringField("Unloading time", unloadingTime);
      jGenerator.writeStringField("Unloading finished", Boolean.toString(ship.unloadFinished));
      jGenerator.writeEndObject();

      jGenerator.writeRaw('\n');
      waitingSum += ship.waitingTime;
      waitingAmount++;
      unloadingDelaySum += ship.unloadingDelay;
      unloadingDelayAmount++;
      maxUnloadingDelay = Long.max(maxUnloadingDelay, ship.unloadingDelay);

    }

    jGenerator.writeStartObject();
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Total unloaded ships",  Long.toString(Service3.unloadedShipsTotal, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Count of ships that were not unloaded",  Long.toString(unloadedUnfinishedCount, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Average queue size",  Long.toString(Service3.averageWaitQueue / 3, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Average wait in queue in minutes",
            Long.toString(waitingAmount/waitingSum, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Max deviation of unloading",
            Long.toString(maxUnloadingDelay, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Average deviation of unloading",
            Long.toString(unloadingDelaySum/unloadingDelayAmount, 10));
    jGenerator.writeRaw('\n');
    long shipCost = Service3.bulkCrane * 30000 + Service3.liquidCrane * 30000 + Service3.containerCrane * 30000;
    jGenerator.writeStringField("Total fine",
            Long.toString(Service3.fineTotal - shipCost, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField(("Bulk fine"), Long.toString(Service3.bulkFine));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField(("Liquid fine"), Long.toString(Service3.liquidFine));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField(("Container fine"), Long.toString(Service3.containerFine));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Total ship cost",
            Long.toString(shipCost, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Total bulk cranes",
            Long.toString(Service3.bulkCrane, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Total liquid cranes",
            Long.toString(Service3.liquidCrane, 10));
    jGenerator.writeRaw('\n');
    jGenerator.writeStringField("Total container cranes",
            Long.toString(Service3.containerCrane, 10));
    jGenerator.writeRaw('\n');

    jGenerator.writeEndObject();
    jGenerator.close();
  }


}
