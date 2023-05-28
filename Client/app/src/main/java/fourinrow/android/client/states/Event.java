package fourinrow.android.client.states;

import org.json.JSONObject;

public class Event {
    private Phase phase;
    private State state;
    private Object data;

    public Event(Phase phase) {
        this.phase = phase;
    }
    public Event(Phase phase, State state) {
        this(phase);
        this.state = state;
    }

    public Event(Phase phase, State state, Object data) {
        this(phase, state);
        this.data = data;
    }

    public Phase getPhase() {
        return phase;
    }
    public State getState() {
        return state;
    }
    public Object getData() { return data; }

}
