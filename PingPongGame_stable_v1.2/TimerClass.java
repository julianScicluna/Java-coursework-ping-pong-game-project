import java.util.ArrayList;
import java.util.LinkedList;

import Helpers.TimerHelpers.TimerEvent;
import Helpers.TimerHelpers.TimerListener;

//My very own Timer helper library... about time (get it? Oh right, there's nobody here...)
public class TimerClass {
	//Declare members of type long regarding timer's duration, time of starting and elapsed time excluding pauses
	private long duration, startTime, elapsedTimeBeforeLatestPause = 0 /*sum of series of elapsed times before the latest pause*/, stoppedAt /*Time at which timer was stopped for prematurely stopped timers*/;
	//Boolean determining timer state: active (out of time?), paused and pending stopping
	private boolean active = false, paused = false, toBeStopped = false;
	//Static boolean to check whether the timer thread and ExecutorService have been disposed of
	private static boolean disposedOf = false;
	//Variable of type TimerState storing the timer state
	private TimerState timerState = TimerState.NOT_STARTED;
	//ArrayList of Threads locked (bound) by the Timer, waiting for its completion
	private final ArrayList<Thread> waitingThreads = new ArrayList<Thread>();
	//Reference to current TimerClass
	protected final TimerClass cinst = this;
	//List of registered TimerListeners
	private final ArrayList<TimerListener> timerListeners = new ArrayList<TimerListener>();
	//Object internally used for thread locking for the duration of a timer
	private final Object lockObj = new Object();
	//Object internally used for thread locking until an event is completed
	private final Object eventCompletionLockObj = new Object();

	/**
	 * <p>An Enum containing a {@code TimerClass} instance's possible states, those being:</p>
	 * <p>{@code NOT_STARTED} - The timer has not yet started counting</p>
	 * <p>{@code ACTIVE} - The timer is currently active and counting</p>
	 * <p>{@code ENDED} - The timer has finished on its own</p>
	 * <p>{@code STOPPED} - The timer has been prematurely stopped (forcefully)</p>
	*/
	//Code adapted from https://www.w3schools.com/java/java_enums.asp
	public static enum TimerState {
		NOT_STARTED,
		ACTIVE,
		ENDED,
		STOPPED
	}

	//This is a special thread wherein one would submit Runnables to complete - reusing the thread - similar to the EDT
	//Beats creating new threads for everything all the time...
	//DO NOT use the timing thread to execute the Runnables, they may take some time to complete, interfering with the other timers
	//private static ExecutorService AbstractComponent.componentEventDispatchThread = Executors.newSingleThreadExecutor();

	//List of active (counting) timers
	private static LinkedList<TimerClass> activeTimerList = new LinkedList<TimerClass>();
	//Static Thread object - CONSTANTLY checks and changes (if applicable) the state of ALL active timers
	private static Thread timerThread = new Thread() {
		//TODO: Fix all code which uses current instance as lock - make it use private, final Object
		@Override
		public void run() {
			//Create a reference to a timer to avoid fumbling around in the LinkedList<TimerClass>, with each retrieval of time complexity O(n)
			TimerClass currentTimer;
			boolean hasStoppedAllTimers = false;
			while (true) {
				//Create a monitor on the list of active timers - avert ConcurrentModificationExceptions
				synchronized(activeTimerList) {
					//Check if the thread is interrupted and exit the loop (end the thread) if so
					if (isInterrupted()) {
						//Wake up any sleeping threads before terminating the thread forever
						for (TimerClass timer : activeTimerList) {
							timer.stop();
						}
						hasStoppedAllTimers = true;
					}
					//Whilst safe in the knowledge that no other thread is accessing the list, perform one cycle of checking active timers
					for (int i = 0; i < activeTimerList.size(); i++) {
						currentTimer = activeTimerList.get(i);
						//Synchronize on the current TIMER (not its lock object), to ensure it is not changed by other threads during its checking and possible stopping
						synchronized(currentTimer) {
							//Check whether the current timer is to be stopped
							if (currentTimer.getActiveStatus() && currentTimer.toBeStopped) {
								currentTimer.toBeStopped = false;
								//Set the timer's internal state to inactive
								currentTimer.setInactiveState();
								currentTimer.timerState = TimerState.STOPPED;
								currentTimer.stoppedAt = System.currentTimeMillis();
								final TimerClass currentTimerFinalReference = currentTimer;
								AbstractComponent.componentEventDispatchThread.submit(new Runnable() {
									@Override
									public void run() {
										synchronized(currentTimerFinalReference.timerListeners) {
											for (int i = 0; i < currentTimerFinalReference.timerListeners.size(); i++) {
												currentTimerFinalReference.timerListeners.get(i).timerStopped(new TimerEvent(currentTimerFinalReference, TimerEvent.TIMER_STOPPED));
												if (currentTimerFinalReference.timerListeners.get(i).once) {
													currentTimerFinalReference.timerListeners.remove(i);
													i--;
												}
											}
										}
									}
								});
								//End of timer stop handling code - invoke method to delete any listeners with the appropriate flags
								currentTimer.setInactiveStateFooter();
								//Wake the waiting thread
								synchronized(currentTimer.eventCompletionLockObj) {
									currentTimer.eventCompletionLockObj.notifyAll();
								}
								//Skip to next timer after decrementing the index as to not skip the next timer
								i--;
								continue;
							}
							//If the timer has run out of time AND IS NOT PAUSED, change its state accordingly
							if (!currentTimer.paused && currentTimer.getRemainingTime() <= 0 && currentTimer.getActiveStatus() /*getRemainingTime() returns 0 if inactive, causing possible bad states if checked improperly*/) {
								//Timer has ended - invoke method to set state accordingly, free any redundant resources and remove from list of active timers
								currentTimer.setInactiveState();
								//The timer has really and truly ended "on its own", not been stopped. Set the state accordingly.
								currentTimer.timerState = TimerState.ENDED;
								//Create a final variable (reference) for access to the current timer in the anonymous class
								final TimerClass currentTimerFinal = currentTimer;
								AbstractComponent.componentEventDispatchThread.submit(new Runnable() {
									@Override
									public void run() {
										synchronized(currentTimerFinal.timerListeners) {
											for (int i = 0; i < currentTimerFinal.timerListeners.size(); i++) {
												currentTimerFinal.timerListeners.get(i).timerStopped(new TimerEvent(currentTimerFinal, TimerEvent.TIMER_FINISHED));
												if (currentTimerFinal.timerListeners.get(i).once) {
													currentTimerFinal.timerListeners.remove(i);
													i--;
												}
											}
										}
									}
								});
								//End of timer finish handling code - invoke method to delete any listeners with the appropriate flags
								currentTimer.setInactiveStateFooter();
								//Decrement the timer due to one less entry
								i--;
							}
						}
					}
					if (isInterrupted()) {
						if (hasStoppedAllTimers) {
							break;
						} else {
							continue;
						}
					}
				}
			}
		}
	};

	/**
	 * Method to add a {@code TimerListener} to a {@code TimerClass} object
	 * @param t - The {@code TimerListener} to add
	 */
	public void addTimerListener(TimerListener t) {
		if (t == null) {
			throw new NullPointerException("Cannot add a null TimerListener to a TimerClass");
		}
		this.timerListeners.add(t);
	}

	/**
	 * Method to remove a {@code TimerListener} to a {@code TimerClass} object 
	 * @param t - The {@code TimerListener} to remove
	 */
	public boolean removeTimerListener(TimerListener t) {
		if (t == null) {
			throw new NullPointerException("Cannot remove a null TimerListener from a TimerClass");
		}
		return this.timerListeners.remove(t);
	}

	/**
	 * A method to get the list of a {@code TimerClass}' {@code TimerListeners}
	 * @return an array of {@code TimerListener}s corresponding to the internal {@code LinkedList<TimerListener>}
	 */
	public TimerListener[] getTimerListeners() {
		return timerListeners.toArray(new TimerListener[timerListeners.size()]);
	}


	/**
	 * Method to dispose of all threads and resources associated with the <code>TimerClass</code> soon. Creating new timers beyond this point should throw an <code>IllegalStateException</code>
	 * @return void
	*/
	public static void stopTimerService() {
		AbstractComponent.componentEventDispatchThread.interrupt();
		timerThread.interrupt();
		TimerClass.disposedOf = true;
	}

	/**
	 * A method to get a timer's state of type <code>TimerState</code>, which can be one of four possible values, those being:
	 * <p>{@code NOT_STARTED} - The timer has not yet started counting</p>
	 * <p>{@code ACTIVE} - The timer is currently active and counting</p>
	 * <p>{@code ENDED} - The timer has finished on its own</p>
	 * <p>{@code STOPPED} - The timer has been prematurely stopped (forcefully)</p>
	 * @return A <code>TimerState</code> object representing the Timer's state
	 */
	public TimerState getTimerState() {
		return timerState;
	}

	/**
	 * Method to dispose of all threads and resources associated with the <code>TimerClass</code> at once (bypassing the event queue). Creating new timers beyond this point should throw an <code>IllegalStateException</code>
	 * @return void
	*/
	public static void stopTimerServiceNow() {
		AbstractComponent.componentEventDispatchThread.interruptNow();
		timerThread.interrupt();
		TimerClass.disposedOf = true;
	}

	public TimerClass() {
		//CRITICAL WARNING: Must check whether the timer has been disposed of first - checking whether the timer thread is alive before checking the class' state may result in the constructor attempting to reinvoke start() on an already finished thread, throwing a misleading IllegalThreadStateException
		if (TimerClass.disposedOf) {
			//If the timer has been disposed of, it can't work. Throw an IllegalStateException when trying to instantiate one in such a case
			throw new IllegalStateException("Cannot instantiate a timer; the class timer's thread has been disposed of");
		} else if (!timerThread.isAlive()) {
			//Start timing thread on the very first constructor invocation
			timerThread.start();
		}
	}

	public synchronized void start(long duration) {
		//Start a timer with specified duration and completion runnable (only changeable through resetting the timer)
		if (this.active) {
			//One timer per instance! Trying to invoke this method while the timer's state is active will throw an exception
			throw new IllegalStateException("Cannot start an already started timer. Instantiate another timer object and use that instead");
		} else {
			//Might give inaccurate time results
			//Initialise object members to define new timer context
			this.timerState = TimerState.ACTIVE;
			this.elapsedTimeBeforeLatestPause = 0;
			this.duration = duration;
			this.startTime = System.currentTimeMillis();
			//Get monitor to activeTimerList - make sure no other thread is currently operating on it to avoid an ConcurrentModificationException
			synchronized(activeTimerList) {
				//Make this timer active - add it to the list of active timers
				activeTimerList.add(this);
			}
			//Set internal timer state variable to true (active)
			this.active = true;
			AbstractComponent.componentEventDispatchThread.submit(new Runnable() {
				@Override
				public void run() {
					synchronized(timerListeners) {
						for (int i = 0; i < timerListeners.size(); i++) {
							timerListeners.get(i).timerStopped(new TimerEvent(cinst, TimerEvent.TIMER_STARTED));
							if (timerListeners.get(i).once) {
								timerListeners.remove(i);
								i--;
							}
						}
					}
				}
			});
		}
	}

	//WORD OF WARNING: <b><i><u>DO NOT</u></i></b> DECLARE THIS METHOD SYNCHRONIZED; There will be two locks. By the time the first thread finishes, another thread will start executing the instructions, only for the second lock (lockObj.wait()) to lock it, to be hung indefinitely
	public void lockUntilCompletion() throws InterruptedException {
		if (this.active) {
			synchronized(this.lockObj) {
				try {
					//Add the thread BEFORE waiting!
					waitingThreads.add(Thread.currentThread());
					this.lockObj.wait();
				} catch (InterruptedException ie) {
					//This should NEVER EVER EVER be caused by the timing thread itself, EVER.
					waitingThreads.remove(Thread.currentThread());
					throw ie;
				}
			}
		} else {
			throw new IllegalStateException("Cannot lock current thread on inactive timer; it will lock indefinitely");
		}
	}
	//Get the total time elapsed during the timer's activity (EXCLUDING PAUSES)
	public long getElapsedTime() {
		//Get the time elapsed before the latest pause and write it to variable "elapsed" of type long
		long elapsed = this.elapsedTimeBeforeLatestPause;
		//Add to elapsed time if the timer is NOT paused
		if (!this.paused) {
			if (System.currentTimeMillis() < this.startTime + this.duration) {
				//If the timer is still active, add the difference between the current time and the timer's start time
				elapsed += System.currentTimeMillis() - this.startTime;
			} else {
				//The timer is effectively no longer active (really and truly the current time is larger than the sum of the timer's start time and duration) - to avoid an elapsed time longer than the duration, add the duration itself instead
				elapsed += this.duration;
			}
		} else if (this.timerState == TimerState.STOPPED) {
			//Timer was prematurely stopped
			elapsed = this.stoppedAt - this.startTime;
		}

		//Return the value referred to by the vaiable "elapsed"
		return elapsed;
	}

	//Get the remaining timer time
	public long getRemainingTime() {
		if (this.active) {
			//Timer in use - apply typical logic
			if (this.paused) {
				//Timer paused - return (new) duration. On pausing, the timer duration is set to the remaining duration at the time of pausing, hence it will return that, considering no elapsed time could have been counted (timer is PAUSED)
				return this.duration;
			} else {
				//return the difference between (the sum of start time and the duration) and the current time -> Remaining time (if it exceeds 0)
				return Math.max(this.startTime + this.duration - System.currentTimeMillis(), 0);
			}
		} else {
			//Timer not in use... render null by making it seem as if out of time
			return 0;
		}
	}
	//Getter method to check the timer's status
	public boolean getActiveStatus() {
		return this.active;
	}

	//Getter method to check whether the timer is paused
	public boolean isPaused() {
		return this.paused;
	}

	//Method to stop the timer. DO NOT synchronise this method - WILL lock the timer thread, which really and truly stops the timer, causing a DEADLOCK!!!
	public void stop() {
		//Check whether timer is active
		if (this.active) {
			synchronized (this) {
				this.toBeStopped = true;
			}
			try {
				synchronized(this.eventCompletionLockObj) {
					this.eventCompletionLockObj.wait();
				}
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		} else {
			//Cannot stop a stopped timer - method invoked in inappropriate context (state). Throw appropriate exception
			throw new IllegalStateException("Cannot stop an inactive timer");
		}
	}

	//Method to signal the timer thread to stop this timer without waiting for. DO NOT synchronise this method - WILL lock the timer thread, which really and truly stops the timer, causing a DEADLOCK!!!
	public void stopAsync() {
		//Check whether timer is active
		if (this.active) {
			synchronized (this) {
				this.toBeStopped = true;
			}
		} else {
			//Cannot stop a stopped timer - method invoked in inappropriate context (state). Throw appropriate exception
			throw new IllegalStateException("Cannot stop an inactive timer");
		}
	}

	//Hidden method, invoked internally both on timer stop and finish AT THE END of the stop/finish code
	protected synchronized void setInactiveStateFooter() {
		//Remove all timers marked for deletion on timer stop
		for (int i = 0; i < timerListeners.size(); i++) {
			if (timerListeners.get(i).deleteOnTimerStop) {
				timerListeners.remove(i);
				i--;
			}
		}
	}

	//Hidden method, invoked internally both on timer stop and finish AT THE BEGINNING of the stop/finish code
	protected void setInactiveState() {
		//Check whether timer is active
		if (this.active) {
			//Set state accordingly - timer is no longer active; set it to false
			this.active = false;
			//Timer is not paused if it is finished or stopped
			this.paused = false;
			//If there are any threads waiting on the timer (lockUntilCompletion()), wake them up!
			if (this.waitingThreads.size() != 0) {
				//This method is synchronized - no need to create synchronized(Object) {} block
				synchronized(this.lockObj) {
					this.lockObj.notifyAll();
				}
				//Remove any previously waiting threads - not waiting anymore!
				this.waitingThreads.clear();
			}
			//Make sure no other thread has monitor on active timer LinkedList, avoid ConcurrentModificationException
			synchronized (activeTimerList) {
				//This timer has been stopped - is no longer active - remove it from the list
				activeTimerList.remove(this);
			}
		} else {
			//Cannot stop an inactive timer - method invoked in inappropriate context (state). Throw appropriate exception
			throw new IllegalStateException("Cannot stop an inactive timer");
		}
	}

	//Pause the timer
	public synchronized void pause() {
		//Check state
		if (this.paused) {
			//Cannot pause an already paused timer
			throw new IllegalStateException("Cannot pause a paused timer");
		} else if (!this.active) {
			//Cannot pause an inactive timer
			throw new IllegalStateException("Cannot pause an inactive timer");
		} else {
			//Set "paused" member to true - setting state
			//paused must ALWAYS be set to true BEFORE changing the duration. A thread could get a bad state where the duration seems to be too low, yet it is not paused, effectively rendering the timer OVER
			this.paused = true;
			//Change the timer's duration; reducing the difference between the timer's start and the current time
			this.duration -= (System.currentTimeMillis() - this.startTime);
			//Add the reduced time to a member of type long "elapsedTimeBeforeLatestPause"
			this.elapsedTimeBeforeLatestPause += (System.currentTimeMillis() - this.startTime);
			AbstractComponent.componentEventDispatchThread.submit(new Runnable() {
				@Override
				public void run() {
					synchronized(timerListeners) {
						for (int i = 0; i < timerListeners.size(); i++) {
							timerListeners.get(i).timerPaused(new TimerEvent(cinst, TimerEvent.TIMER_PAUSED));
							if (timerListeners.get(i).once) {
								timerListeners.remove(i);
								i--;
							}
						}
					}
				}
			});
		}
	}
	public synchronized void resume() {
		//Get state
		if (!this.paused) {
			//Invalid (Illegal) state - cannot resume a timer which is not paused
			throw new IllegalStateException("Cannot resume a timer which is not paused");
		} else if (!this.active) {
			//Cannot resume an inactive timer
			throw new IllegalStateException("Cannot resume an inactive timer");
		} else {
			//Set the starting time to the time of the method's invocation - the time from pausing is ignored this way, for the duration was changed before pausing. On resumption, the start time is changed in order to ignore the time elapsed during pause.
			this.startTime = System.currentTimeMillis();
			//Set paused state accordingly (not paused)
			this.paused = false;
			AbstractComponent.componentEventDispatchThread.submit(new Runnable() {
				@Override
				public void run() {
					synchronized(timerListeners) {
						for (int i = 0; i < timerListeners.size(); i++) {
							timerListeners.get(i).timerResumed(new TimerEvent(cinst, TimerEvent.TIMER_RESUMED));
							if (timerListeners.get(i).once) {
								timerListeners.remove(i);
								i--;
							}
						}
					}
				}
			});
		}
	}

	@Override
	public String toString() {
		return "Timer: {\nElapsed time: " + this.getElapsedTime() + "\nRemaining time: " + this.getRemainingTime() + "\nTimer state: " + this.getTimerState() + "\n}";
	}
}