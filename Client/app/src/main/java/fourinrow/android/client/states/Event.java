package fourinrow.android.client.states;

public class Event {
    private Phase phase;
    private State state;

    public Event(Phase phase, State state) {
        this.phase = phase;
        this.state = state;
    }

    public Phase getPhase() {
        return phase;
    }

    public State getState() {
        return state;
    }
}
