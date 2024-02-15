public class AnimationEvent {
    public static final int ANIMATION_STARTED = 0, ANIMATION_STOPPED = 1, ANIMATION_ENDED = 2;
    protected long when;
    protected Object target;
    protected int eventType;
    public AnimationEvent(long when, Object target, int eventType) {
        this.when = when;
        this.target = target;
        this.eventType = eventType;
    }

    /**
     * A getter method which gets this {@code AnimationEvent}'s target.
     * @return An {@code Object} or any of its subclasses representing this {@code AnimationEvent}'s target.
     */
    public Object getTarget() {
        return target;
    }
    /**
     * A getter method which gets this {@code AnimationEvent}'s time of dispatching.
     * @return A {@code long} determining the time at which this event was dispatched
     */
    public long getWhen() {
        return when;
    }

    /**
     * <p>A getter method which gets this {@code AnimationEvent}'s type. This can be one of the following types:</p>
     * <p>{@code ANIMATION_STARTED} - indicates that this event signifies the starting of an animation</p>
     * <p>{@code ANIMATION_ENDED} - indicates that this event signifies the starting of an animation</p>
     * <p>{@code ANIMATION_STOPPED} - indicates that this event signifies the premature or forceful stopping of an animation</p>
     * @return An {@code int} determining the type of this event
     */
    public int getEventType() {
        return eventType;
    }
}
