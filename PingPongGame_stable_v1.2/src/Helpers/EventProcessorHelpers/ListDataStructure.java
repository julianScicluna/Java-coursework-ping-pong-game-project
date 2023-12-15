package Helpers.EventProcessorHelpers;

//Generics taken from https://www.geeksforgeeks.org/generics-in-java/
public interface ListDataStructure<T> {
    /**
     * A method to get the next element of the data structure without modifying the data structure
     * @return The element or null/an exception from an implementation-specific list
     */
    public T peek();

    /**
     * A method to get the next element of the data structure and modify the data structure accordingly
     * @return The element or null/an exception from an implementation-specific list
     */
    public T process();

    /**
     * A method to add an element to the data structure's internal list
     * @param elem The element to add
     */
    public void add(T elem);

    public T[] getContents();
}