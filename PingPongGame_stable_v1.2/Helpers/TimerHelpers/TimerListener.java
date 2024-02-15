package Helpers.TimerHelpers;


public abstract class TimerListener {
	public abstract void timerStarted(TimerEvent e);
	public abstract void timerStopped(TimerEvent e);
	public abstract void timerPaused(TimerEvent e);
	public abstract void timerResumed(TimerEvent e);
	public abstract void timerFinished(TimerEvent e);
	public boolean once = false, deleteOnTimerStop = false;
}
