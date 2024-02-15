package Helpers.TimerHelpers;


public class TimerEvent {
	protected Object source;
	protected long timestamp;
	protected int eventType;

	public static final int TIMER_STARTED = 0, TIMER_STOPPED = 1, TIMER_PAUSED = 2, TIMER_RESUMED = 3, TIMER_FINISHED = 4;

	public TimerEvent(Object source, int eventType) {
		this.source = source;
		this.eventType = eventType;
		this.timestamp = System.currentTimeMillis();
	}

	public Object getSource() {
		return this.source;
	}
	public long getTimestamp() {
		return this.timestamp;
	}
	public int getEventType() {
		return this.eventType;
	}
	@Override
	public String toString() {
		return "TimerEvent:\n" + "\tEvent fired at (ms sinch epoch): " + this.timestamp + "\nSource: {" + this.source + "}";
	}
}