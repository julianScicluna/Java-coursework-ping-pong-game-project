import java.util.LinkedList;

public class EventDispatchThread extends Thread {
    private LinkedList<Runnable> eventQueue = new LinkedList<Runnable>();
    private boolean acceptNewTasks = true;
    private final Object lock = new Object();
    @Override
    public void run() {
        try {
            while (true) {
                if (this.isInterrupted()) {
                    break;
                }
                if (this.eventQueue.size() == 0) {
                    if (this.acceptNewTasks) {
                        synchronized(lock) {
                            //'this', albeit a subclass of Thread is being treated as a java.lang.Object here
                            lock.wait();
                        }
                    } else {
                        break;
                    }
                }
                eventQueue.get(0).run();
                eventQueue.removeFirst();
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        //Dispose of all resources allocated here (if any)
    }

    @Override
    public void interrupt() {
        acceptNewTasks = false;
    }
    public void interruptNow() {
        super.interrupt();
    }

    public boolean submit(Runnable task) {
        if (this.acceptNewTasks) {
            if (this.eventQueue.size() == 0) {
                synchronized(lock) {
                    lock.notifyAll();
                }
            }
            this.eventQueue.add(task);
            return true;
        } else {
            return false;
        }
    }
    public EventDispatchThread() {
        this.start();
    }
}