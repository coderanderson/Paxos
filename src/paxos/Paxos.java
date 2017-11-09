package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;

/**
 * This class is the main class you need to implement paxos instances.
 */
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

    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */
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


    /**
     * The application wants Paxos to start agreement on instance seq,
     * with proposed value v. Start() should start a new thread to run
     * Paxos on instance seq. Multiple instances can be run concurrently.
     *
     * Hint: You may start a thread using the runnable interface of
     * Paxos object. One Paxos object may have multiple instances, each
     * instance corresponds to one proposed value/command. Java does not
     * support passing arguments to a thread, so you may reset seq and v
     * in Paxos object before starting a new thread. There is one issue
     * that variable may change before the new thread actually reads it.
     * Test won't fail in this case.
     *
     * Start() just starts a new thread to initialize the agreement.
     * The application will call Status() to find out if/when agreement
     * is reached.
     */
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
                    System.out.println("proposal of peer " + me + " is " + currProposal);
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
                    System.out.println("Prepare Ok number is: " + prepareOkNum);

                    maxProposal = currHighestN;

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
                            System.out.println("" + me + " " + map.get(currThreadSeq).state);
                            System.out.println("" + me + " " + Status(currThreadSeq).state);
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
                }
            }
        }).start();
    }

    @Override
    public void run(){
        //Your code here
    }

    public int newProposal(int maxProposal, int me) {
        return (maxProposal + 1) * 1000 + me;
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
                System.out.println("now proposal: " + req.proposal + " pre proposal: " + highestPrepare);
                state = false;
            }
        }

        maxProposal = highestPrepare;

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

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
        if(seq > doneSeq[me])
            doneSeq[me] = seq;
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
        int max = 0;
        for(int key: map.keySet()) {
            if(key > max)
                max = key;
        }
        return max;
    }

    /**
     * Min() should return one more than the minimum among z_i,
     * where z_i is the highest number ever passed
     * to Done() on peer i. A peers z_i is -1 if it has
     * never called Done().
     *
     * Paxos is required to have forgotten all information
     * about any instances it knows that are < Min().
     * The point is to free up memory in long-running
     * Paxos-based servers.
     *
     * Paxos peers need to exchange their highest Done()
     * arguments in order to implement Min(). These
     * exchanges can be piggybacked on ordinary Paxos
     * agreement protocol messages, so it is OK if one
     * peers Min does not reflect another Peers Done()
     * until after the next instance is agreed to.
     *
     * The fact that Min() is defined as a minimum over
     * all Paxos peers means that Min() cannot increase until
     * all peers have been heard from. So if a peer is dead
     * or unreachable, other peers Min()s will not increase
     * even if all reachable peers call Done. The reason for
     * this is that when the unreachable peer comes back to
     * life, it will need to catch up on instances that it
     * missed -- the other peers therefore cannot forget these
     * instances.
     */
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



    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
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
     * Please don't change these four functions.
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
