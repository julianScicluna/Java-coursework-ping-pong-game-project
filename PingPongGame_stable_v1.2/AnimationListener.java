public abstract class AnimationListener {
    /**
     * An abstract method of an {@code AnimationListener}, invoked when the animation it is assigned to has started
     * @param e The {@code AnimationEvent} carrying data about the event
     */
    public abstract void animationStarted(AnimationEvent e);
    /**
     * An abstract method of an {@code AnimationListener}, invoked when the animation it is assigned to has finished
     * @param e The {@code AnimationEvent} carrying data about the event
     */
    public abstract void animationEnded(AnimationEvent e);
    /**
     * An abstract method of an {@code AnimationListener}, invoked when the animation it is assigned to has been prematurely (forcefully) stopped
     * @param e The {@code AnimationEvent} carrying data about the event
     */
    public abstract void animationStopped(AnimationEvent e);
    /**
     * A {@code boolean} to indicate whether or not this {@code AnimationListener} should be removed on first execution
     */
    boolean once = false;
    /**
     * A {@code boolean} to indicate whether or not this {@code AnimationListener} should be removed when its animation has finished
     */
    boolean removeOnCompletion = false;
}
