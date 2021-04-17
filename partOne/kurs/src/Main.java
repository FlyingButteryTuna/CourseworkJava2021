import services.Service2;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutionException;

public class Main
{
  public static void main(String args[]) throws IOException, ExecutionException, InterruptedException, BrokenBarrierException {
    Service2.generateJsonWithSchedule();
  }
}
