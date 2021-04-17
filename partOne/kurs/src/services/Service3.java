package services;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Service3 {
  public static long unloadedShipsTotal = 0;
  public static long averageWaitQueue = 0;
  private static long averageWaitQueueTmp;
  public static long fineTotal = 0;
  public static long bulkCrane;
  public static long liquidCrane;
  public static long containerCrane;
  public static long bulkFine;
  public static long liquidFine;
  public static long containerFine;
  public static AtomicInteger waitFine = new AtomicInteger(0);
  private static int fineNotUnloaded = 0;
  private static long time;
  private static int day;
  private static final ArrayList<Queue<Service1.Ship>> shipsQueues = new ArrayList<>(3);

  private static CyclicBarrier barrier;
  private static final ArrayList<ArrayList<Service1.Ship>> currentlyUnloadingShips =
          new ArrayList<>(3);

  private static final int craneCost = 30000;
  private static final ArrayList<Service1.Ship> shipsResult = new ArrayList<>();
  private static final Queue<Service1.Ship> waitingQueue = new LinkedList<>();


  static private class Crane implements Runnable {
    final private Service1.CargoTypes craneType;

    public Crane(Service1.CargoTypes craneType) {
      this.craneType = craneType;
    }

    public void run() {
      Service1.Ship additionalShip = null;
      while (true) {

        boolean flag = false;
        Service1.Ship ship = null;

        synchronized (waitingQueue) {
          if (!waitingQueue.isEmpty())
            ship = waitingQueue.remove();
        }

        if (ship != null) {
          long plannedLengthOfStay = ship.lengthOfStay;
          ship.lengthOfStay += ship.unloadingDelay;
          if (additionalShip != null){
            additionalShip.amountOfCranesCurrentlyUnloading--;
            additionalShip = null;

          }
          while (!ship.unloadFinished) {
            synchronized (ship) {
              ship.unloadFinished = unload(ship, craneType);
            }

            try {
              barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {

              if (!ship.unloadFinished) {
                ship.unloadingFinishedTime = time;
                countFine(ship, plannedLengthOfStay);
              }
              flag = true;
              break;
            }
          }

          if (!flag)
            countFine(ship, plannedLengthOfStay);
          else
            break;

        } else {
          if (additionalShip == null || additionalShip.unloadFinished){
            synchronized (currentlyUnloadingShips) {
              int i = 0;

              while (i < currentlyUnloadingShips.get(craneType.ordinal()).size()){
                additionalShip = currentlyUnloadingShips.get(craneType.ordinal()).get(i);
                if (additionalShip != null) {
                  if (additionalShip.amountOfCranesCurrentlyUnloading == 1) {
                    additionalShip.amountOfCranesCurrentlyUnloading++;
                    break;
                  }
                }
                i++;
              }
            }
          }

          try {
            barrier.await();
          } catch (InterruptedException | BrokenBarrierException e) {
            break;
          }
        }
      }
    }

    private void countFine(Service1.Ship ship, long plannedLengthOfStay) {

      synchronized (shipsResult) {
        ship.unloadingTime = ship.unloadingFinishedTime - ship.realArrivalTime;
        ship.waitingTime = ship.realArrivalTime - (ship.arrivalTime + (long) (ship.arrivalDay - 1) * 24 * 60);
        shipsResult.add(ship);

      }

      long realLengthOfStay = ship.unloadingFinishedTime - ship.realArrivalTime;

      if (realLengthOfStay > plannedLengthOfStay) {
        waitFine.addAndGet((int) ((realLengthOfStay - plannedLengthOfStay) / 60) * 100);
      }
      long waitingFineTmp = ship.realArrivalTime -
              (ship.arrivalTime + (long) (ship.arrivalDay - 1) * 24 * 60);

      if (waitingFineTmp > 0) {
        waitFine.addAndGet((int) ((waitingFineTmp / 60) * 100));
      }
    }
  }

  public static ArrayList<Service1.Ship> simulate(String path) throws IOException {
    currentlyUnloadingShips.add(new ArrayList<>());
    currentlyUnloadingShips.add(new ArrayList<>());
    currentlyUnloadingShips.add(new ArrayList<>());
    shipsQueues.add(0, null);
    shipsQueues.add(1, null);
    shipsQueues.add(2, null);

    ArrayList<Service1.Ship> ships = getShipsFromJson(path);
    System.out.printf("Ships before shift: %d\n", ships.size());
    shiftSchedule(path, ships);
    ships = getShipsFromJson(path);
    System.out.printf("Ships after shift: %d\n", ships.size());

    //System.out.println(simulate_with_single_crane_type(120, Service1.CargoTypes.BULK, ships, path));

    System.out.println("BULK:");
    ArrayList<Service1.Ship> finalStats = new ArrayList<>(findOptimal(Service1.CargoTypes.BULK, path));
    System.out.println("LIQUID:");
    finalStats.addAll(findOptimal(Service1.CargoTypes.LIQUID, path));
    System.out.println("CONTAINER:");
    finalStats.addAll(findOptimal(Service1.CargoTypes.CONTAINER, path));

    return finalStats;
  }

  private static synchronized void incTime() {
    long tmp = 1439;
    if ((time % (24L * 60 * day)) == tmp) {
      day++;
    }
    time++;
  }

  private static int simulateWithSingleCraneType(int amountOfCranes,
                                                     Service1.CargoTypes craneType,
                                                     String path) throws  IOException {

    barrier = new CyclicBarrier(amountOfCranes + 2);
    ArrayList<Service1.Ship> ships = getShipsFromJson(path);
    Collections.sort(ships);
    Queue<Service1.Ship> queue = convertShipsListToQueue(ships, craneType);
    shipsQueues.add(craneType.ordinal(), queue);
    day = 1;
    time = 0;
    waitFine.set(0);

    ExecutorService executor = Executors.newFixedThreadPool(amountOfCranes + 1);
    shipsResult.clear();
    waitingQueue.clear();
    currentlyUnloadingShips.get(craneType.ordinal()).clear();
    averageWaitQueueTmp = 0;
    fineNotUnloaded = 0;
    for (int i = 0; i < amountOfCranes + 1; i++) {
      Crane crane = new Crane(craneType);
      executor.submit(crane);
    }

    long averageWaitingTmp = 0;
    long minutes = 44640;
    do {
      Service1.Ship tmp = shipsQueues.get(craneType.ordinal()).peek();
      if (tmp != null && ((tmp.arrivalDay - 1) * 24 * 60L + tmp.arrivalTime) == time) {
        waitingQueue.add(tmp);
        shipsQueues.get(craneType.ordinal()).remove(tmp);
      }

      try {
        barrier.await();

      } catch (InterruptedException | BrokenBarrierException e) {
        break;
      }
      averageWaitingTmp += waitingQueue.size();
      incTime();
    } while (day != 31);

    long waitFineTmp = 0;
    for (Service1.Ship ship : waitingQueue) {

        waitFineTmp += (44640 - ((ship.arrivalDay - 1) * 24 * 60L + ship.arrivalTime)) / 60 * 100;
    }
    fineNotUnloaded += waitFineTmp;
    waitFine.addAndGet((int) waitFineTmp);
    averageWaitQueueTmp += averageWaitingTmp / minutes;

    executor.shutdownNow();

    barrier.reset();
    barrier = new CyclicBarrier(1);

    return waitFine.get();
  }

  private static ArrayList<Service1.Ship> findOptimal(Service1.CargoTypes craneType, String path) throws IOException {
    int minCostCrane;
    int minCost = (simulateWithSingleCraneType(0, craneType, path) + craneCost);
    int tmp;
    int totalCraneCost;
    System.out.println(minCost - craneCost);
    System.out.println(craneCost);
    System.out.println(1);
    int i = 4;
    ArrayList<Service1.Ship> stats;
    long averageWaitQueueTmp2;
    long fineNotUnloadedTmp;

    do {
      totalCraneCost = (i + 1) * craneCost;
      tmp = (simulateWithSingleCraneType(i, craneType, path) + totalCraneCost);
      /*System.out.println(tmp - totalCraneCost);
      System.out.println(totalCraneCost);
      System.out.println(tmp);
      System.out.println(i + 1);*/
      i += 5;
    } while ((tmp - totalCraneCost) > craneCost);
    i -= 5;
    minCost = tmp;
    minCostCrane = i + 1;
    stats = shipsResult;
    averageWaitQueueTmp2 = averageWaitQueueTmp;
    fineNotUnloadedTmp = fineNotUnloaded;
    i--;

    while ((tmp - totalCraneCost) < craneCost || i == 0){
      totalCraneCost = (i + 1) * craneCost;
      tmp = (simulateWithSingleCraneType(i, craneType, path) + totalCraneCost);
      /*System.out.println(tmp - totalCraneCost);
      System.out.println(totalCraneCost);
      System.out.println(tmp);
      System.out.println(i + 1);*/
      if ((minCost > tmp) && (tmp - totalCraneCost < craneCost)) {
        minCost = tmp;
        minCostCrane = i + 1;
        stats = shipsResult;
        averageWaitQueueTmp2 = averageWaitQueueTmp;
        fineNotUnloadedTmp = fineNotUnloaded;
      }
      i--;
    }

    fineTotal += minCost;
    unloadedShipsTotal += shipsResult.size();
    averageWaitQueue += averageWaitQueueTmp2;
    switch (craneType) {
      case BULK -> {
        bulkCrane = minCostCrane;
        bulkFine = minCost - (long) minCostCrane * craneCost;
      }
      case LIQUID -> {
        liquidCrane = minCostCrane;
        liquidFine = minCost - (long) minCostCrane * craneCost;
      }
      case CONTAINER -> {
        containerCrane = minCostCrane;
        containerFine = minCost - (long) minCostCrane * craneCost;
      }
    }
    shipsResult.addAll(shipsQueues.get(craneType.ordinal()));
    System.out.printf("cost: %d\ncranes: %d\nn: %d\n", minCost, minCostCrane, fineNotUnloadedTmp);
    return stats;
  }

  private static boolean unload(Service1.Ship ship, Service1.CargoTypes craneType) {
    if ((ship.arrivalTime + (long) (ship.arrivalDay - 1) * 60 * 24) > time || ship.unloadFinished) {
      return false;
    }
    if (!ship.hasArrived) {
      ship.hasArrived = true;
      ship.realArrivalTime = time;
      ship.amountOfCranesCurrentlyUnloading = 1;
      currentlyUnloadingShips.get(craneType.ordinal()).add(ship);
    }

    long craneEffecieny = 1;
    if (ship.amountOfCranesCurrentlyUnloading == 2) {
      //ship.amountOfCranesCurrentlyUnloading--;
      craneEffecieny = 2;
    }

    if (ship.lengthOfStay > 0) {
      ship.lengthOfStay -= craneEffecieny;
    } else {
      ship.unloadingFinishedTime = time;
      ship.unloadFinished = true;
      synchronized (currentlyUnloadingShips) {
        currentlyUnloadingShips.get(craneType.ordinal()).remove(ship);
      }
    }

    return ship.unloadFinished;
  }

  private static Queue<Service1.Ship> convertShipsListToQueue(ArrayList<Service1.Ship> ships,
                                                                  Service1.CargoTypes shipType) {
    Queue<Service1.Ship> result = new LinkedList<>();
    for (Service1.Ship ship : ships) {
      if (ship.cargoType == shipType) {
        result.add(ship);
      }
    }
    return result;
  }

  private static ArrayList<Service1.Ship> getShipsFromJson(String path) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    ArrayList<Service1.Ship> result = mapper.readValue(new File(path),
            new TypeReference<ArrayList<Service1.Ship>>() {
            });
    Collections.sort(result);
    return result;
  }


  private static void shiftSchedule(String path, ArrayList<Service1.Ship> ships) throws IOException {
    ArrayList<Service1.Ship> shipsToRemove = new ArrayList<>();
    for (Service1.Ship ship : ships) {
      long newArrivalTime = ((ship.arrivalDay - 1) * 24 * 60L + ship.arrivalTime) +
              (new Random().nextInt(10080) - 10080);
      int newArrivalDay = ((int) TimeUnit.MINUTES.toDays(newArrivalTime) + 1);
      long newArrivalHours = (int) TimeUnit.MINUTES.toHours(newArrivalTime)
              - ((int) TimeUnit.MINUTES.toDays(newArrivalTime) * 24L);
      long newArrivalMinutes = (TimeUnit.MINUTES.toMinutes(newArrivalTime)
              - (TimeUnit.MINUTES.toHours(newArrivalTime) * 60));
      ship.arrivalDay = newArrivalDay;
      ship.arrivalTime = newArrivalHours * 60 + newArrivalMinutes;
      if (ship.arrivalTime < 0 || ship.arrivalTime > 44640) {
        shipsToRemove.add(ship);
      }
    }
    ships.removeAll(shipsToRemove);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    File file = new File(path);
    JsonGenerator g = mapper.getFactory().createGenerator(new FileOutputStream(file));
    mapper.writeValue(g, ships);
  }
}
