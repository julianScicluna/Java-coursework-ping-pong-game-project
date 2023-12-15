import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JPanel;

//THIS IS THE LATEST VERSION - UPDATE OLDER VERSIONS

//An abstract class representing an abstract animation
public abstract class JAnimation<T> extends JPanel {
    //Using data structures which mimic (and internally use in the case of ArrayList<T>) resizable arrays
    private final LinkedList<AnimationListener> listeners = new LinkedList<AnimationListener>();
    private static final ArrayList<JAnimation<?>> activeAnimations = new ArrayList<JAnimation<?>>();
    private static final ExecutorService listenerRunner = Executors.newSingleThreadExecutor();
    private static final Thread activeAnimationsRepainter = new Thread() {
        @Override
        public void run() {
            //Loop until loop is broken out of
            while (true) {
                //Check whether the thread has been interrupted
                if (this.isInterrupted()) {
                    //Break from the loop, ending the thread
                    break;
                }
                //Get a lock on the ArrayList before iterating over it to avoid a ConcurrentModificationException
                synchronized(activeAnimations) {
                    //Iterate over the ArrayList
                    JAnimation<?> animation;
                    for (int i = 0; i < activeAnimations.size(); i++) {
                        animation = activeAnimations.get(i);
                        //Repaint the active component
                        animation.repaint();
                        //If the animation is over, stop it
                        if (animation.startTime + animation.duration <= System.currentTimeMillis()) {
                            animation.stop();
                            AnimationListener l;
                            for (int j = 0; j < animation.listeners.size(); j++) {
                                l = animation.listeners.get(j);
                                final AnimationListener currentListenerFinalReference = l;
                                final JAnimation<?> currentAnimationFinalReference = animation;
                                listenerRunner.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        currentListenerFinalReference.animationEnded(new AnimationEvent(System.currentTimeMillis(), currentAnimationFinalReference, AnimationEvent.ANIMATION_ENDED));
                                    }
                                });
                                if (l.once || l.removeOnCompletion) {
                                    animation.listeners.remove(j);
                                    //Decrement the index to avoid skipping an animation
                                    j--;
                                }
                            }
                            //Same index due to removal - ALWAYS put at the end unless trying to obtain previous animation index
                            i--;
                        }
                    }
                }
            }
            System.out.println("JAnimation updater thread stopped");
        }
    };
    public static void stopAnimationServiceNow() {
        activeAnimationsRepainter.interrupt();
    }

    public void addAnimationListener(AnimationListener l) {
        if (l == null) {
            throw new NullPointerException("Cannot add a null AnimationListener");
        } else {
            listeners.add(l);
        }
    }
    
    public boolean removeActionListener(AnimationListener l) {
        if (l == null) {
            throw new NullPointerException("Cannot remove a null AnimationListener");
        } else {
            return listeners.remove(l);
        }
    }

    public JAnimation() {
		//Start animation thread on the very first constructor invocation
		if (!activeAnimationsRepainter.isAlive()) {
			activeAnimationsRepainter.start();
		}
    }
    //This object should be private - only the internal methods should interrupt/wait on it
    private final Object hiddenAnimationLock = new Object();
    //Starting time of the animation since epoch time
    public long startTime;
    //Animation duration in milliseconds
    protected int duration;
    //Boolean determining annimation state
    protected boolean active = false;
    //Methods which must be overridden in non-abstract instances of AbstractAnimation
    //A function defining the relationship between an animation state and a variable (typically animation time)
    protected abstract T animationFunction(double animationVariable);
    //Final (cannot be overridden), synchronized (cannot be invoked by multiple threads simulataneously) method to start the animation
    public final synchronized void start(int duration) {
        if (this.active) {
            throw new IllegalStateException("Cannot start an animation in progress (active animation)");
        } else {
            this.active = true;
            this.startTime = System.currentTimeMillis();
            this.duration = duration;
            activeAnimations.add(this);
        }
        AnimationListener l;
        for (int i = 0; i < this.listeners.size(); i++) {
            l = this.listeners.get(i);
            final AnimationListener currentListenerFinalReference = l;
            final JAnimation<?> currentAnimationFinalReference = this;
            listenerRunner.submit(new Runnable() {
                @Override
                public void run() {
                    currentListenerFinalReference.animationStarted(new AnimationEvent(System.currentTimeMillis(), currentAnimationFinalReference, AnimationEvent.ANIMATION_STARTED));
                }
            });
            if (l.once) {
                this.listeners.remove(i);
                //Decrement the index to avoid skipping an animation
                i--;
            }
        }
    }
    /**
     *A method to lock the current thread until an animation is completed or aborted (prematurely stopped)
     */
    //DO NOT make this method synchronised - other threads may remain locked onto it indefinitely
    public void lockUntilCompletion() throws InterruptedException {
        if (this.active) {
            try {
                synchronized(this.hiddenAnimationLock) {
                    this.hiddenAnimationLock.wait();
                }
            } catch (InterruptedException ie) {
                //Set some internal state before re-throwing the exception - DO NOT invoke stop() as that notifies all threads waiting on the object. This method is also used internally by stop()
                this.setInactivityState();
                throw ie;
            }
        } else {
            throw new IllegalStateException("Cannot wait on an inactive animation (current thread will be hung indefinitely)");
        }
    }
    private void setInactivityState() {
        if (!this.active) {
            throw new IllegalStateException("Cannot stop a stopped (inactive) animation");
        } else {
            this.active = false;
            activeAnimations.remove(this);
            synchronized(this.hiddenAnimationLock) {
                this.hiddenAnimationLock.notifyAll();
            }
        }
    }
    //Final (cannot be overridden), synchronized (cannot be invoked by multiple threads simulataneously) method to stop the animation
    public final synchronized void stop() {
        if (!this.active) {
            throw new IllegalStateException("Cannot stop a stopped (inactive) animation");
        } else {
            //Stopping requires disabling the animation (rendering it inactive) along with waking all sleeping threads
            setInactivityState();
            AnimationListener l;
            for (int i = 0; i < this.listeners.size(); i++) {
                l = this.listeners.get(i);
                final AnimationListener currentListenerFinalReference = l;
                final JAnimation<?> currentAnimationFinalReference = this;
                listenerRunner.submit(new Runnable() {
                    @Override
                    public void run() {
                        currentListenerFinalReference.animationStopped(new AnimationEvent(System.currentTimeMillis(), currentAnimationFinalReference, AnimationEvent.ANIMATION_STOPPED));
                    }
                });
                if (l.once) {
                    this.listeners.remove(i);
                    //Decrement the index to avoid skipping an animation
                    i--;
                }
            }
        }
    }
    public boolean isActive() {
        return this.active;
    }
    public int getAnimationDuration() {
        return this.duration;
    }
    public int getAnimationElapsedTime() {
        if (this.active) {
            return (int) (System.currentTimeMillis() - this.startTime);
        } else {
            return 0;
        }
    }
}

//Class for initial ping pong game animation
class BallAnimationState {
    final double[] ballCoords = new double[] {0.0, 0.0};
    final double[] slider1Coords = new double[] {0.0, 0.0};
    final double[] slider2Coords = new double[] {0.0, 0.0};
    final double[] sliderDimensions = new double[] {0.0, 0.0};
}