package kvpaxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID=11L;
    // Your data here
    public String key;
    public int seq;
    public int value;

    // Your constructor and methods here
    public Request(String key, int value, int seq) {
        this.key = key;
        this.value = value;
        this.seq = seq;
    }
}
