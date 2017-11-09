package kvpaxos;
import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Server implements KVPaxosRMI {

    ReentrantLock mutex;
    Registry registry;
    Paxos px;
    int me;

    String[] servers;
    int[] ports;
    KVPaxosRMI stub;

    // Your definitions here
    HashMap<String, Integer> kv;
    HashMap<Integer, Boolean> seqMap;
    List<Op> logs;
    int lastSeq;

    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);

        // Your initialization code here
        kv = new HashMap<>();
        logs = new ArrayList<>();
        lastSeq = 1;
        seqMap = new HashMap<>();


        try{
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    //helper functions
    public Op wait(int seq) {
        int to = 10;
        while (true) {
            Paxos.retStatus ret = this.px.Status(seq);
            if (ret.state == State.Decided) {
                return Op.class.cast(ret.v);
            }
            try {
                Thread.sleep(to);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (to < 1000) {
                to = to * 2;
            }
        }
    }

    public void Apply(Op op) {
        if(op.op.equals("Get")) {
            logs.add(op);
        } else if(op.op.equals("Put")){
            kv.put(op.key, op.value);
        }
        seqMap.put(op.ClientSeq, true);
        px.Done(lastSeq);
        lastSeq++;
    }

    public void Spread(Op op) {
        Op log;
        Boolean status = false;
        while(!status) {
            int seq = lastSeq;
            State state = px.Status(lastSeq).state;
            Op val = (Op)(px.Status(lastSeq).v);
            if(state.equals(State.Decided)) {
                log = val;
            } else {
                px.Start(seq, op);
                log = wait(seq);
            }

            status = (op.ClientSeq == log.ClientSeq);
            Apply(log);
        }
    }


    // RMI handlers
    public Response Get(Request req){
        // Your code here
        Op op = new Op("Get", req.seq, req.key, 0);
        Spread(op);
        Response res;
        if(kv.containsKey(req.key)) {
            res = new Response("ok", kv.get(req.key));
        } else {
            res = new Response("NoKey", 0);
        }
        return res;
    }

    public Response Put(Request req){
        // Your code here
        if(seqMap.containsKey(req.seq)) {
            return new Response("ok", 0);
        }

        Op newOp = new Op("Put", req.seq, req.key, req.value);
        Spread(newOp);
        return new Response("ok", 0);
    }


}
