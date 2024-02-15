package Helpers.EventProcessorHelpers;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * <p>A thread-safe implementation of a queue, with methods allowing one to add, peek at or process an element in the queue. Elements can be added with the flow of the queue or with urgency, skipping all other elements in the queue</p>
 * @param <T> A type parameter representing the type of elements for this queue
*/
public class ThreadSafeQueue<T> extends Queue<T> {
    private final LinkedList<T> eventQueue = new LinkedList<T>();
    /**
     * A method to add an element to the queue, to be processed after all previous elements have been processed
     * @param elem The element to add to the queue
     */
    public void add(T elem) {
        synchronized(eventQueue) {
            eventQueue.add(elem);
        }
    }

    /**
     * A method to get the latest element of the queue and remove it from the queue. Returns the element or {@code null} if there is no next element (i.e.: the queue is empty)
     * @return The element or {@code null} if there is no next element (i.e.: the queue is empty)
     */
    public T process() {
        try {
            synchronized(eventQueue) {
                if (this.urgentElementCount > 0) {
                    this.urgentElementCount--;
                }
                return eventQueue.removeFirst();
            }
        } catch (NoSuchElementException nsee) {
            return null;
        }
    }

    /**
     * Peeks at the next element without interfering with the queue
     * @return
     */
    public T peek() {
        try {
            synchronized(eventQueue) {
                return eventQueue.get(0);
            }
        } catch (IndexOutOfBoundsException iobe) {
            return null;
        }
    }

    /**
     * A method to add an element to the very front of the queue, to be processed immediately, skipping all other elements
     * @param elem The element to add to the queue
     */
    public void addWithHighestPriority(T elem) {
        synchronized(eventQueue) {
            this.urgentElementCount++;
            eventQueue.add(0, elem);
        }
    }

    /**
     * Get a copy of the queue's current contents
     * @return The generic array of type T[] containing the queue's current contents
     */
    public T[] getContents() {
        synchronized(eventQueue) {
            return (T[]) eventQueue.toArray();
        }
    }
}
