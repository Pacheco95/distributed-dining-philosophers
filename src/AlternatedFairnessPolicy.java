import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class AlternatedFairnessPolicy implements FairnessPolicy {
    @Override
    public Socket whoWillEat(Map<Socket, Integer> philosophers) {
        ArrayList<Map.Entry<Socket, Integer>> entries = new ArrayList<>(philosophers.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        return entries.get(0).getKey();
    }
}
