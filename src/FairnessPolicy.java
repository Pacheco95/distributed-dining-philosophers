import java.net.Socket;
import java.util.Map;

public interface FairnessPolicy {
    Socket whoWillEat (Map<Socket, Integer> philosophers);
}
