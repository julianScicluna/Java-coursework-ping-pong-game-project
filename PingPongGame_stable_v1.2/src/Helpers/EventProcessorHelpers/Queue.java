package Helpers.EventProcessorHelpers;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * <p>An implementation of a queue, with methods allowing one to add, peek at or process an element in the queue. Elements can be added with the flow of the queue or with urgency, skipping all other elements in the queue</p>
 * <p><b>This queue implementation is NOT thread-safe!</b></p>
 * @param <T> A type parameter representing the type of elements for this queue
*/
public class Queue<T> implements ListDataStructure<T> {
    protected final LinkedList<T> eventQueue = new LinkedList<T>();
    protected int urgentElementCount = 0;
    /**
     * A method to add an element to the queue, to be processed after all previous elements have been processed
     * @param elem The element to add to the queue
     */
    public void add(T elem) {
        eventQueue.add(elem);
    }

    /**
     * A method to get the latest element of the queue and remove it from the queue. Returns the element or {@code null} if there is no next element (i.e.: the queue is empty)
     * @return The element or {@code null} if there is no next element (i.e.: the queue is empty)
     */
    public T process() {
        try {
            if (this.urgentElementCount > 0) {
                this.urgentElementCount--;
            }
            return eventQueue.removeFirst();
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
            return eventQueue.get(0);
        } catch (IndexOutOfBoundsException iobe) {
            return null;
        }
    }

    /**
     * A method to add an element to the very front of the queue, to be processed immediately, skipping all other elements
     * @param elem The element to add to the queue
     */
    public void addWithHighestPriority(T elem) {
        this.urgentElementCount++;
        eventQueue.add(0, elem);
    }

    public boolean isNextElementUrgent() {
        return urgentElementCount > 0;
    }

    /**
     * Get a copy of the queue's current contents
     * @return The generic array of type T[] containing the queue's current contents
     */
    public T[] getContents() {
        return (T[]) eventQueue.toArray();
    }
}
