import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

public class RandomFairnessPolicy implements FairnessPolicy {
    @Override
    public Socket whoWillEat(Map<Socket, Integer> philosophers) {
        ArrayList<Map.Entry<Socket, Integer>> entries = new ArrayList<>(philosophers.entrySet());
        return entries.get((int) (Math.random() * entries.size())).getKey();
    }
}
