/*This file is part of the project which implement Paxos in java with java RMI. 
*I choose this code because it has moderate complexity and shows my coding style.
*/

package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;

public class Paxos implements PaxosRMI, Runnable{

    ReentrantLock mutex;
    String[] peers; // hostname
    int[] ports; // host port
    int me; // index into peers[]

    Registry registry;
    PaxosRMI stub;

    AtomicBoolean dead;// for testing
    AtomicBoolean unreliable;// for testing

    // Your data here
    int currSeq;
    Object currValue;

    int npaxos;

    int isDone;
    HashMap<Integer, Instance> map = new HashMap<>();
    int[] doneSeq;
    int maxProposal;
    //for acceptor
    int highestPrepare;
    int highestAccept;
    Instance highestAcceptV;
    
    
    public Paxos(int me, String[] peers, int[] ports){

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here
        npaxos = peers.length;
        maxProposal = 0;
        doneSeq = new int[npaxos];
        for(int i = 0; i < npaxos; i++) {
            doneSeq[i] = -1;
        }

        // register peers, do not modify this part
        try{
            System.setProperty("java.rmi.server.hostname", this.peers[this.me]);
            registry = LocateRegistry.createRegistry(this.ports[this.me]);
            stub = (PaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("Paxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public Response Call(String rmi, Request req, int id){
        Response callReply = null;

        PaxosRMI stub;
        try{
            Registry registry=LocateRegistry.getRegistry(this.ports[id]);
            stub=(PaxosRMI) registry.lookup("Paxos");
            if(rmi.equals("Prepare"))
                callReply = stub.Prepare(req);
            else if(rmi.equals("Accept"))
                callReply = stub.Accept(req);
            else if(rmi.equals("Decide"))
                callReply = stub.Decide(req);
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            System.out.println("fail");
            return null;
        }
        return callReply;
    }

    public void Start(int seq, Object value){
        // Your code here
        currSeq = seq;
        currValue = value;
        if(seq < Min()) return;
        (new Thread("" + seq * 100 + "" + me) {
            public void run() {
                while(true) {
                    System.out.println("1");
                    int currThreadSeq = currSeq;
                    Object currThreadValue = currValue;

                    int currHighestN = 0;
                    Object currHighestV = currThreadValue;

                    int prepareOkNum = 0;
                    int acceptOkNum = 0;

                    Response[] response = new Response[npaxos];
                    int currProposal = newProposal(maxProposal, me);
                    //System.out.println("proposal of peer " + me + " is " + currProposal);
                    Request req = new Request(currProposal, currThreadSeq, null, 0, 0);
                    response[me] = Prepare(req);

                    //send prepare message
                    for (int i = 0; i < npaxos; i++) {
                        if (i == me) continue;
                        response[i] = Call("Prepare", req, i);
                        if(response[i] != null)
                            System.out.println("Peer " + me + " " + response[i].state);
                        if (isDead()) break;
                    }

                    for (int i = 0; i < npaxos; i++) {
                        if (response[i] != null && response[i].state == true) {
                            prepareOkNum++;
                            if (response[i].highestAccept != -1) {
                                if (response[i].highestAccept > currHighestN) {
                                    currHighestN = response[i].highestAccept;
                                    currHighestV = response[i].highestAcceptV;
                                }
                            }
                        }
                    }
                    System.out.println("2");
                    //System.out.println("Prepare Ok number is: " + prepareOkNum);

                    maxProposal = currHighestN > maxProposal ? currHighestN : maxProposal;

                    //send accept message
                    if (prepareOkNum > npaxos / 2) {
                        req = new Request(currProposal, currThreadSeq, currHighestV, 0, 0);
                        response[me] = Accept(req);
                        for (int i = 0; i < npaxos; i++) {
                            if (i == me) continue;
                            response[i] = Call("Accept", req, i);
                            System.out.println("accept");
                            if (isDead()) break;
                        }

                        for (int i = 0; i < npaxos; i++) {
                            if (response[i] != null && response[i].state == true) {
                                acceptOkNum++;
                            }
                        }

                        System.out.println("3");
                        //send decide message
                        if (acceptOkNum > npaxos / 2) {
                            req = new Request(currProposal, currThreadSeq, currHighestV, me, doneSeq[me]);
                            map.put(currThreadSeq, new Instance(paxos.State.Decided, currProposal, currProposal, currHighestV));
                            //System.out.println("" + me + " " + map.get(currThreadSeq).state);
                            //System.out.println("" + me + " " + Status(currThreadSeq).state);
                            for (int i = 0; i < npaxos; i++) {
                                if (i == me) continue;
                                response[i] = Call("Decide", req, i);
                                System.out.println("" + me + " decide response");
                                if (isDead()) break;
                            }
                        }
                    }

                    System.out.println("" + me + " " + Status(currThreadSeq).state);

                    if (Status(currThreadSeq).state.equals(paxos.State.Decided)) {
                        break;
                    }

                    maxProposal = maxProposal > currProposal ? maxProposal : currProposal;

                    try {
                        Thread.sleep(100000);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    public void run(){
        //Your code here
    }

    public int newProposal(int maxProposal, int me) {
        return (maxProposal + 1) * 10 + me;
    }


    // RMI handler
    public Response Prepare(Request req){
        // your code here
        int highestPrepare = 0;
        boolean state;
        int highestAccept = -1;
        State currState = State.Pending;
        Object highestAcceptV = null;

        if(!map.containsKey(req.seq)) {
            state = true;
            highestPrepare = req.proposal;
        } else {
            highestPrepare = map.get(req.seq).highestPrepare;
            highestAccept = map.get(req.seq).highestAccept;
            highestAcceptV = map.get(req.seq).highestAcceptV;
            currState = map.get(req.seq).state;
            if(req.proposal > highestPrepare) {
                highestPrepare = req.proposal;
                state = true;
            } else {
                //System.out.println("now proposal: " + req.proposal + " pre proposal: " + highestPrepare);
                state = false;
            }
        }

        maxProposal = (maxProposal > highestPrepare ? maxProposal : highestPrepare);

        map.put(req.seq, new Instance(currState, highestPrepare, highestAccept, highestAcceptV));

        return new Response(state, highestAccept, highestAcceptV); 
    }

    public Response Accept(Request req){
        // your code here
        boolean state;
        if(!map.containsKey(req.seq)) {
            state = true;
        } else {
            if(req.proposal > highestPrepare) {
                state = true;                
            } else {
                state = false;
            }
        }
        if(state) {
            map.put(req.seq, new Instance(State.Pending, req.proposal, req.proposal, req.value));
            return new Response(true, 0, null);
        } else {
            return new Response(false, 0, null);
        }
    }

    public Response Decide(Request req){
        // your code here
        Instance newInstance = new Instance(State.Decided, req.proposal, req.proposal, req.value);
        map.put(req.seq, newInstance);
        doneSeq[req.me] = req.done;
        return new Response(true, 0, null);
    }

    public void Done(int seq) {
        // Your code here
        if(seq > doneSeq[me])
            doneSeq[me] = seq;
    }

    public int Max(){
        // Your code here
        int max = 0;
        for(int key: map.keySet()) {
            if(key > max)
                max = key;
        }
        return max;
    }

    public int Min(){
        // Your code here
        int min = doneSeq[me];
        for(int i = 0; i < doneSeq.length; i++) {
            if(doneSeq[i] < min) 
                min = doneSeq[i];
        }

        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Instance> pair = (Map.Entry)it.next();
            int key = pair.getKey();
            if(key > min || !map.get(key).state.equals(State.Decided))
                continue;
            it.remove(); // avoids a ConcurrentModificationException
        }
        return min + 1;
    }

    public retStatus Status(int seq){
        // Your code here
        if(seq < Min()) return new retStatus(State.Forgotten, null);
        if(!map.containsKey(seq)) return new retStatus(State.Pending, null);
        else {
            return new retStatus(map.get(seq).state, map.get(seq).highestAcceptV);
        }
    }

    /**
     * helper class for Status() return
     */
    public class retStatus{
        public State state;
        public Object v;

        public retStatus(State state, Object v){
            this.state = state;
            this.v = v;
        }
    }

    /**
     * Tell the peer to shut itself down.
     * For testing.
     */
    public void Kill(){
        this.dead.getAndSet(true);
        if(this.registry != null){
            try {
                UnicastRemoteObject.unexportObject(this.registry, true);
            } catch(Exception e){
                System.out.println("None reference");
            }
        }
    }

    public boolean isDead(){
        return this.dead.get();
    }

    public void setUnreliable(){
        this.unreliable.getAndSet(true);
    }

    public boolean isunreliable(){
        return this.unreliable.get();
    }


}
