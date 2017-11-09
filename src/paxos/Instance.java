package paxos;

public class Instance {
	public State state;
	public int highestPrepare;
	public int highestAccept;
	public Object highestAcceptV;

	public Instance(State state, int highestPrepare, int highestAccept, Object highestAcceptV) {
		this.state = state;
		this.highestPrepare = highestPrepare;
		this.highestAccept = highestAccept;
		this.highestAcceptV = highestAcceptV;
	}
}