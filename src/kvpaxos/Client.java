package kvpaxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;


public class Client {
    String[] servers;
    int[] ports;

    // Your data here


    public Client(String[] servers, int[] ports){
        this.servers = servers;
        this.ports = ports;
        // Your initialization code here
    }

    private int rand() {
        Random rand = new Random();
        int max = Integer.MAX_VALUE;
        return rand.nextInt(max) + 1;
    }

    /**
     * Call() sends an RMI to the RMI handler on server with
     * arguments rmi name, request message, and server id. It
     * waits for the reply and return a response message if
     * the server responded, and return null if Call() was not
     * be able to contact the server.
     *
     * You should assume that Call() will time out and return
     * null after a while if it doesn't get a reply from the server.
     *
     * Please use Call() to send all RMIs and please don't change
     * this function.
     */
    public Response Call(String rmi, Request req, int id){
        Response callReply = null;
        KVPaxosRMI stub;
        try{
            Registry registry= LocateRegistry.getRegistry(this.ports[id]);
            stub=(KVPaxosRMI) registry.lookup("KVPaxos");
            if(rmi.equals("Get"))
                callReply = stub.Get(req);
            else if(rmi.equals("Put")){
                callReply = stub.Put(req);}
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }

    // RMI handlers
    public Integer Get(String key){
        // Your code here
        Request req = new Request(key, 0, rand());
        int index = 0;
        int to = 10;
        Response res = null;
        while(true) {
            res = Call("Get", req, index);
            if(res != null && res.status.equals("ok")) {
                return res.value;
            }
            try {
                Thread.sleep(to);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (to < 1000) {
                to = to * 2;
            }
            index = (index + 1) % servers.length;
        }
    }

    public boolean Put(String key, Integer value){
        // Your code here
        Request req = new Request(key, value, rand());
        Response res = null;
        int index = 0;
        int to = 10;
        while(true) {
            res = Call("Put", req, index);
            if(res != null && res.status.equals("ok")) {
                return true;
            }
            try {
                Thread.sleep(to);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (to < 1000) {
                to = to * 2;
            }
            index = (index + 1) % servers.length;
        }
    }

}
