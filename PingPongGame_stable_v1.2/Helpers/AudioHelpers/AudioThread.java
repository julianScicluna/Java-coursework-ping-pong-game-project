package Helpers.AudioHelpers;
public class AudioThread extends Thread {
    private boolean toRestart = false, isAudioThreadPaused = false;

    /**
     * A getter method which returns the value of the {@code toRestart} flag
     * @return A boolean value which equals the {@code toRestart} flag's state at the time of invocation
     */
    public boolean shouldRestart() {
        return this.toRestart;
    }

    public void resetRestartState() {
        this.toRestart = false;
    }

    public void signifyRestart() {
        this.toRestart = true;
    }

    /**
     * A getter method which returns the audio thread's {@code isAudioThreadPaused} flag value.
     * @return A boolean value which equals the {@code isAudioThreadPaused} flag's state at the time of invocation
     */
    public boolean isAudioThreadPaused() {
        return isAudioThreadPaused;
    }

    /**
     * <p>A method which signals the audio thread to pause in between tracks.</p>
     * <p>NB: This method <b>IS NOT</b> an alternative to pausing the audio player. It is only to pause the audio thread itself in scenarios where the audio player cannot be paused (e.g.: it is inactive)</p>
     * @param isAudioThreadPaused - The variable determining the new pause state (true or false)
     */
    public void setAudioThreadPaused(boolean isAudioThreadPaused) {
        this.isAudioThreadPaused = isAudioThreadPaused;
    }
}
