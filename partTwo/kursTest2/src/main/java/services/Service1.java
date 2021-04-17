package services;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.Collections;
import java.util.Random;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@RestController
public class Service1
{
  final static private int amountOfDays = 30;
  final static private int maxShipsPerDay = 6;
  final static private int minShipsPerDay = 3;

  final static private int bulkCraneEfficiency = 7;
  final static private int liquidCraneEfficiency = 10;
  final static private int containerCraneEfficiency = 20;
  final static private int maxWorkHours = 24;
  final static private int minCargoWeight = 20000;
  final static private int maxCargoWeight = 40000;

  public enum CargoTypes
  {
    BULK,
    LIQUID,
    CONTAINER
  }
  
  public static class Ship implements Comparable<Ship>
  {
    public String name;
    public int arrivalDay;

    public long arrivalTime;
    public CargoTypes cargoType;
    public int cargoWeight;
    public long lengthOfStay;
    public long realArrivalTime;
    public boolean hasArrived;
    public boolean unloadFinished;
    public int amountOfCranesCurrentlyUnloading;
    public long unloadingDelay;
    public long unloadingFinishedTime;
    public long waitingTime;
    public long unloadingTime;

    Ship(){}

    Ship(String name, int arrivalDay, long arrivalTime, CargoTypes cargoType, int cargoWeight, long unloadingDelay)
    {
      this.name = name;
      this.arrivalDay = arrivalDay;
      this.arrivalTime = arrivalTime;
      this.cargoType = cargoType;
      this.cargoWeight = cargoWeight;



      this.lengthOfStay = switch (cargoType) {
        case BULK -> cargoWeight / bulkCraneEfficiency;
        case LIQUID -> cargoWeight / liquidCraneEfficiency;
        case CONTAINER -> cargoWeight / containerCraneEfficiency;
      };

      this.realArrivalTime = 0;
      this.hasArrived = false;
      this.unloadFinished = false;
      this.amountOfCranesCurrentlyUnloading = 0;
      this.unloadingDelay = unloadingDelay;
      this.unloadingFinishedTime = 0;
      this.unloadingTime = 0;
      this.waitingTime = 0;
    }

    @Override
    public int compareTo(Ship o) {
      long tmp = ((arrivalDay - 1) * 24L * 60 + arrivalTime) -
              ((o.arrivalDay - 1) * 24L * 60 + o.arrivalTime);
      if(tmp < 0)
        return -1;
      else if (tmp > 0)
        return 1;


      return 1;
    }
  }

  @GetMapping("/scheduleGenerate")
  public static String GenerateSchedule() throws JsonProcessingException {
    final Random random = new Random();
    ArrayList<Ship> ships = new ArrayList<>();
    for (int i = 1; i <= amountOfDays; i++)
    {
      int amountOfShips = random.nextInt(maxShipsPerDay) + minShipsPerDay;
      int hoursRangeOfShipArrival = maxWorkHours / amountOfShips;
      int minHour = 0;
      int maxHour =+ hoursRangeOfShipArrival;
      for (int j = 1; j <= amountOfShips; j++)
      {
        Ship tmp = generateShip(minHour, maxHour, i);
        ships.add(tmp);
        minHour += hoursRangeOfShipArrival;
        maxHour += hoursRangeOfShipArrival;
        while (minHour >= 24) {
          minHour -= 24;
        }

        while (maxHour >= 24) {
          maxHour -= 24;
        }
      }
    }
    Collections.sort(ships);
    //printSchedule(ships);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    String tmp = mapper.writeValueAsString(ships);
    //System.out.println(tmp);
    return tmp;
  }

  private static Ship generateShip(int minHour, int maxHour, int day)
  {
    String name = generateRandomWord();
    int arrivalHours = createRandomIntBetween(minHour, maxHour);
    int arrivalMinutes = createRandomIntBetween(0, 59);
    long arrivalTime = (day - 1) * 24L + arrivalHours * 60L + arrivalMinutes;

    CargoTypes cargoType = CargoTypes.values()[new Random().nextInt(CargoTypes.values().length)];
    int cargoWeight = createRandomIntBetween(minCargoWeight, maxCargoWeight);
    long unloadingDelay = createRandomIntBetween(0, 1441);
    return new Ship(name, day, arrivalTime, cargoType, cargoWeight, unloadingDelay);
  }

  private static int createRandomIntBetween(int start, int end)
  {
    return start + (int) Math.round(Math.random() * (end - start));
  }

  public static String generateRandomWord()
  {
    String randomString;
    Random random = new Random();

    char[] word = new char[random.nextInt(8)+3];
    for(int j = 0; j < word.length; j++)
    {
      word[j] = (char)('a' + random.nextInt(26));
    }
    randomString = new String(word);

    return randomString;
  }

  public static void printSchedule(ArrayList<Service1.Ship> ships)
  {
    for (Service1.Ship ship : ships) {
      long hours = (TimeUnit.MINUTES.toHours(ship.arrivalTime) -
              (TimeUnit.MINUTES.toDays(ship.arrivalTime)) * 24);

      long minutes = (TimeUnit.MINUTES.toMinutes(ship.arrivalTime)
              - (TimeUnit.MINUTES.toHours(ship.arrivalTime) * 60));
      System.out.printf("Day: %d\n", ship.arrivalDay);
      System.out.printf("Name: %s\n", ship.name);
      System.out.printf("time: %d:%d\n", hours, minutes);
      String cargoType = switch (ship.cargoType) {
        case BULK -> "BULK";
        case LIQUID -> "LIQUID";
        case CONTAINER -> "CONTAINER";
      };
      System.out.printf("Cargo type: %s\n", cargoType);
      System.out.printf("Cargo weight: %d\n", ship.cargoWeight);
      int days = (int) (TimeUnit.MINUTES.toDays(ship.lengthOfStay));
      hours = (TimeUnit.MINUTES.toHours(ship.lengthOfStay) -
              (TimeUnit.MINUTES.toDays(ship.lengthOfStay)) * 24);

      minutes = (TimeUnit.MINUTES.toMinutes(ship.lengthOfStay)
              - (TimeUnit.MINUTES.toHours(ship.lengthOfStay) * 60));
      System.out.printf("Length of stay: %d:%d:%d\n", days, hours, minutes);
    }
  }
}
