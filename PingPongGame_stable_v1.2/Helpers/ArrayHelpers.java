package Helpers;
import java.lang.reflect.Array;

public class ArrayHelpers {
    //Constructor to prevent this utility class from being instaniated
    private ArrayHelpers() {
        throw new UnsupportedOperationException("Cannot instantiate StringHelpers utility class");
    }

    /**
     * A generic array helper method to search for an element in linear time. Returns its index or -1 if the element is not found in the array
     * @param <T> Type parameter to accept any non-primitive and its corresponding array type
     * @param arr The array to search
     * @param obj The object to search for
     * @return An integer representing the element's index in the specified array or -1
    */
    public static <T> int indexOf(T[] arr, T elem) {
        if (elem == null) {
            throw new NullPointerException("Element to look up cannot be null");
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) {
                throw new NullPointerException("Element in array cannot be null");
            }
            if (arr[i].equals(elem)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * An array helper method to search for an element in linear time. Returns its index or -1 if the element is not found in the array
     * @param arr The array to search
     * @param obj The object to search for
     * @return An integer representing the element's index in the specified array or -1
    */
    public static int indexOf(double[] arr, double elem) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == elem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * An array helper method to search for an element in linear time. Returns its index or -1 if the element is not found in the array
     * @param arr The array to search
     * @param obj The object to search for
     * @return An integer representing the element's index in the specified array or -1
    */
    public static int indexOf(int[] arr, int elem) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == elem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A generic array helper method to search for an element in linear time within specified bounds. Returns its index or -1 if the element is not found in the array. The returned index is <b>NOT</b> relative to the start index
     * @param <T> Type parameter to accept any non-primitive and its corresponding array type
     * @param arr The array to search
     * @param obj The object to search for
     * @param start The index to start searching at. Cannot be lower than zero or exceed the end index or the last element's index
     * @param end The index at which to stop the search. Cannot be lower than zero or exceed the last element's index
     * @return An integer representing the element's index in the specified array or -1
    */
    public static <T> int indexOf(T[] arr, T elem, int start, int end) {
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("The start and end search bounds of the array must be at least 0");
        } else if (end < start) {
            throw new IllegalArgumentException("The end index cannot be before the start index");
        } else if (start >= arr.length || end >= arr.length) {
            throw new IllegalArgumentException("The start and end search bounds cannot exceed the array's last index (which is its length subtracted by 1, in this case " + (arr.length - 1) + ")");
        } else if (elem == null) {
            throw new NullPointerException("The search target cannot be null");
        }
        for (int i = start; i < end; i++) {
            if (arr[i] == null) {
                throw new NullPointerException("The element index in the array cannot be null");
            }
            if (arr[i].equals(elem)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * An array helper method to search for an element in linear time within specified bounds. Returns its index or -1 if the element is not found in the array. The returned index is <b>NOT</b> relative to the start index
     * @param arr The array to search
     * @param obj The object to search for
     * @param start The index to start searching at. Cannot be lower than zero or exceed the end index or the last element's index
     * @param end The index at which to stop the search. Cannot be lower than zero or exceed the last element's index
     * @return An integer representing the element's index in the specified array or -1
    */
    public static int indexOf(double[] arr, double elem, int start, int end) {
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("The start and end search bounds of the array must be at least 0");
        } else if (end < start) {
            throw new IllegalArgumentException("The end index cannot be before the start index");
        } else if (start >= arr.length || end >= arr.length) {
            throw new IllegalArgumentException("The start and end search bounds cannot exceed the array's last index (which is its length subtracted by 1, in this case " + (arr.length - 1) + ")");
        }
        for (int i = start; i < end; i++) {
            if (arr[i] == elem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * An array helper method to search for an element in linear time within specified bounds. Returns its index or -1 if the element is not found in the array. The returned index is <b>NOT</b> relative to the start index
     * @param arr The array to search
     * @param obj The object to search for
     * @param start The index to start searching at. Cannot be lower than zero or exceed the end index or the last element's index
     * @param end The index at which to stop the search. Cannot be lower than zero or exceed the last element's index
     * @return An integer representing the element's index in the specified array or -1
    */
    public static int indexOf(int[] arr, int elem, int start, int end) {
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("The start and end search bounds of the array must be at least 0");
        } else if (end < start) {
            throw new IllegalArgumentException("The end index cannot be before the start index");
        } else if (start >= arr.length || end >= arr.length) {
            throw new IllegalArgumentException("The start and end search bounds cannot exceed the array's last index (which is its length subtracted by 1, in this case " + (arr.length - 1) + ")");
        }
        for (int i = start; i < end; i++) {
            if (arr[i] == elem) {
                return i;
            }
        }
        return -1;
    }

    /**
     * This array helper method applies the merge sort to an array of {@code double}s
     * @param arr The array to apply the merge sort to
     * @param ascending Whether or not the sort the array in ascending order. {@code true} instructs the method to sort in ascending order, whilst {@code false} instructs the method to sort in descending order
     * @return A merge-sorted copy of the original array
     */
    public static double[] mergeSort(double[] arr, boolean ascending) {
        double temp;
        double[][] halves;
        int[] indicesReached = new int[] {0, 0};
        if (arr.length > 1) {
            halves = new double[][] {mergeSort(trim(arr, 0, arr.length/2), ascending), mergeSort(trim(arr, arr.length/2, arr.length), ascending)};
            for (int i = 0; i < arr.length; i++) {
                if (indicesReached[0] >= halves[0].length && !(indicesReached[1] >= halves[1].length)) {
                    arr[i] = halves[1][indicesReached[1]];
                    indicesReached[1]++;
                } else if (indicesReached[1] >= halves[1].length && !(indicesReached[0] >= halves[0].length)) {
                    arr[i] = halves[0][indicesReached[0]];
                    indicesReached[0]++;
                } else if (indicesReached[0] < halves[0].length && indicesReached[1] < halves[1].length) {
                    if (ascending) {
                        if (halves[0][indicesReached[0]] <= halves[1][indicesReached[1]]) {
                            arr[i] = halves[0][indicesReached[0]];
                            indicesReached[0]++;
                        } else if (halves[0][indicesReached[0]] >= halves[1][indicesReached[1]]) {
                            arr[i] = halves[1][indicesReached[1]];
                            indicesReached[1]++;
                        }
                    } else {
                        if (halves[0][indicesReached[0]] >= halves[1][indicesReached[1]]) {
                            arr[i] = halves[0][indicesReached[0]];
                            indicesReached[0]++;
                        } else if (halves[0][indicesReached[0]] <= halves[1][indicesReached[1]]) {
                            arr[i] = halves[1][indicesReached[1]];
                            indicesReached[1]++;
                        }
                    }
                }
            }
        }
        return arr;
    }

    /**
     * This array helper method applies the merge sort to an array of {@code int}s
     * @param arr The array to apply the merge sort to
     * @param ascending Whether or not the sort the array in ascending order. {@code true} instructs the method to sort in ascending order, whilst {@code false} instructs the method to sort in descending order
     * @return A merge-sorted copy of the original array
     */
    public static int[] mergeSort(int[] arr, boolean ascending) {
        int temp;
        int[][] halves;
        int[] indicesReached = new int[] {0, 0};
        if (arr.length > 1) {
            halves = new int[][] {mergeSort(trim(arr, 0, arr.length/2), ascending), mergeSort(trim(arr, arr.length/2, arr.length), ascending)};
            for (int i = 0; i < arr.length; i++) {
                if (indicesReached[0] >= halves[0].length && !(indicesReached[1] >= halves[1].length)) {
                    arr[i] = halves[1][indicesReached[1]];
                    indicesReached[1]++;
                } else if (indicesReached[1] >= halves[1].length && !(indicesReached[0] >= halves[0].length)) {
                    arr[i] = halves[0][indicesReached[0]];
                    indicesReached[0]++;
                } else if (indicesReached[0] < halves[0].length && indicesReached[1] < halves[1].length) {
                    if (ascending) {
                        if (halves[0][indicesReached[0]] <= halves[1][indicesReached[1]]) {
                            arr[i] = halves[0][indicesReached[0]];
                            indicesReached[0]++;
                        } else if (halves[0][indicesReached[0]] >= halves[1][indicesReached[1]]) {
                            arr[i] = halves[1][indicesReached[1]];
                            indicesReached[1]++;
                        }
                    } else {
                        if (halves[0][indicesReached[0]] >= halves[1][indicesReached[1]]) {
                            arr[i] = halves[0][indicesReached[0]];
                            indicesReached[0]++;
                        } else if (halves[0][indicesReached[0]] <= halves[1][indicesReached[1]]) {
                            arr[i] = halves[1][indicesReached[1]];
                            indicesReached[1]++;
                        }
                    }
                }
            }
        }
        return arr;
    }

    /**
     * This array helper method applies the bubble sort to an array of {@code int}s
     * @param arr The array to apply the merge sort to
     * @param ascending Whether or not the sort the array in ascending order. {@code true} instructs the method to sort in ascending order, whilst {@code false} instructs the method to sort in descending order
     * @return A bubble-sorted copy of the original array
     */
    public static int[] bubbleSort(int[] arr, boolean ascending) {
        boolean noShiftsInPass;
        int temp;
        do {
            noShiftsInPass = true;
            for (int i = 0; i < arr.length; i++) {
                if (ascending) {
                    if (arr[i] > arr[i + 1]) {
                        temp = arr[i];
                        arr[i] = arr[i + 1];
                        arr[i + 1] = temp;
                        noShiftsInPass = false;
                    }
                } else {
                    if (arr[i] < arr[i + 1]) {
                        temp = arr[i];
                        arr[i] = arr[i + 1];
                        arr[i + 1] = temp;
                        noShiftsInPass = false;
                    }
                }
            }
        } while (!noShiftsInPass);
        return arr;
    }

    /**
     * This array helper method applies the bubble sort to an array of {@code double}s
     * @param arr The array to apply the merge sort to
     * @param ascending Whether or not the sort the array in ascending order. {@code true} instructs the method to sort in ascending order, whilst {@code false} instructs the method to sort in descending order
     * @return A bubble-sorted copy of the original array
     */
    public static double[] bubbleSort(double[] arr, boolean ascending) {
        boolean noShiftsInPass;
        double temp;
        do {
            noShiftsInPass = true;
            for (int i = 0; i < arr.length - 1; i++) {
                if (ascending) {
                    if (arr[i] > arr[i + 1]) {
                        temp = arr[i];
                        arr[i] = arr[i + 1];
                        arr[i + 1] = temp;
                        noShiftsInPass = false;
                    }
                } else {
                    if (arr[i] < arr[i + 1]) {
                        temp = arr[i];
                        arr[i] = arr[i + 1];
                        arr[i + 1] = temp;
                        noShiftsInPass = false;
                    }
                }
            }
        } while (!noShiftsInPass);
        return arr;
    }
    /**
     * An array helper method to display the contents of an array as a string
     * @param <T> The type of elements in the array to stringify
     * @param arr The array to stringify
     * @return A string representing the array's contents
     */
    public static <T> String stringifyArray(T[] arr) {
        StringBuilder sb = new StringBuilder();
        System.out.println(arr.length);
        sb.append("[");
        for (int i = 0; i < arr.length; i++) {
            if (i == arr.length - 1) {
                sb.append(arr[i].toString());
            } else {
                sb.append(arr[i].toString() + ", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    public static int findIndexString(String[] arr, String elem, boolean caseSensitive, int from, int to) {
        if (elem == null) {
            throw new NullPointerException("Element to look up cannot be null");
        } else if (to < from) {
            throw new IllegalArgumentException("The end cannot be before the start");
        }
        if (!caseSensitive) {
            elem = elem.toLowerCase();
        }
        for (int i = from; i < to; i++) {
            if (arr[i] == null) {
                continue;
            }
            if (caseSensitive) {
                if (arr[i].equals(elem)) {
                    return i;
                }
            } else {
                if (arr[i].toLowerCase().equals(elem)) {
                    return i;
                }
            }
        }
        return -1;
    }

    //Time complexity: O(n^2)
    /**
     * This array helper method removes all duplicates from an array. NB: This method <b>DOES NOT</b> modify the original array, rather it returns a copy
     * @param <T> The type of the elements in the array to operate on
     * @param origArr The array to manipulate
     * @return A modified copy of the array, with no duplicate elements
     */
    public static <T> T[] removeDuplicates(T[] origArr) {
        boolean foundDuplicate;
        int latestIndex = 0;
        T[] arr = (T[]) Array.newInstance(origArr.getClass().getComponentType(), origArr.length);
        for (int i = 0; i < origArr.length; i++) {
            foundDuplicate = false;
            for (int j = 0; j < arr.length; j++) {
                if (origArr[i] == arr[j] && i != j) {
                    foundDuplicate = true;
                    break;
                }
            }
            if (!foundDuplicate) {
                arr[latestIndex] = origArr[i];
                latestIndex++;
            }
        }
        T[] shortenedArr = (T[]) Array.newInstance(origArr.getClass().getComponentType(), latestIndex);
        for (int i = 0; i < shortenedArr.length; i++) {
            shortenedArr[i] = arr[i];
        }
        return shortenedArr;
    }

    public static int highestNumIndex(double[] arr) {
        int highestNumIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[highestNumIndex] <= arr[i]) {
                highestNumIndex = i;
            }
        }
        return highestNumIndex;
    }
    public static int lowestNumIndex(double[] arr) {
        int lowestNumIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[lowestNumIndex] >= arr[i]) {
                lowestNumIndex = i;
            }
        }
        return lowestNumIndex;
    }
    public static int highestNumIndex(int[] arr) {
        int highestNumIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[highestNumIndex] <= arr[i]) {
                highestNumIndex = i;
            }
        }
        return highestNumIndex;
    }
    public static int lowestNumIndex(int[] arr) {
        int lowestNumIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[lowestNumIndex] >= arr[i]) {
                lowestNumIndex = i;
            }
        }
        return lowestNumIndex;
    }
    public static double average(double[] set) {
        double total = 0;
        for (int i = 0; i < set.length; i++) {
            total += set[i];
        }
        return total/set.length;
    }
    public static double mode(double[] set) {
        double[] numArr = new double[set.length];
        int[] freqArr = new int[set.length];
        int index;
        for (int i = 0; i < freqArr.length; i++) {
            freqArr[i] = 0;
        }
        for (int i = 0; i < numArr.length; i++) {
            index = indexOf(numArr, set[i]);
            if (index == -1) {
                numArr[i] = set[i];
                freqArr[i]++;
            } else {
                freqArr[index]++;
            }
        }
        return numArr[highestNumIndex(freqArr)];
    }
    public static int mode(int[] set) {
        int[] numArr = new int[set.length];
        int[] freqArr = new int[set.length];
        int index;
        for (int i = 0; i < freqArr.length; i++) {
            freqArr[i] = 0;
        }
        for (int i = 0; i < numArr.length; i++) {
            index = indexOf(numArr, set[i]);
            if (index == -1) {
                numArr[i] = set[i];
                freqArr[i]++;
            } else {
                freqArr[index]++;
            }
        }
        return numArr[highestNumIndex(freqArr)];
    }
    public static <T> T[] shuffle(T[] arr) {
        T temp;
        int randomIndex;
        for (int i = arr.length - 1; i > 0; i--) {
            do {
                randomIndex = (int) (Math.random() * arr.length);
            } while (randomIndex == i);
            temp = arr[i];
            arr[i] = arr[randomIndex];
            arr[randomIndex] = temp;
        }
        return arr;
    }
    public static double[] shuffle(double[] arr) {
        double temp;
        int randomIndex;
        for (int i = arr.length - 1; i > 0; i--) {
            do {
                randomIndex = (int) (Math.random() * arr.length);
            } while (randomIndex == i);
            temp = arr[i];
            arr[i] = arr[randomIndex];
            arr[randomIndex] = temp;
        }
        return arr;
    }
    public static int[] shuffle(int[] arr) {
        int temp;
        int randomIndex;
        for (int i = arr.length - 1; i > 0; i--) {
            do {
                randomIndex = (int) (Math.random() * arr.length);
            } while (randomIndex == i);
            temp = arr[i];
            arr[i] = arr[randomIndex];
            arr[randomIndex] = temp;
        }
        return arr;
    }
    public static <T> T[] trim(T[] arr, int start, int end) {
        if (end <= start) {
            throw new IllegalArgumentException("End position at or before starting position");
        }
        T[] newArr = (T[]) Array.newInstance(arr.getClass().getComponentType(), end - start);
        for (int i = 0; i < end - start; i++) {
            newArr[i] = arr[i + start];
        }
        return (T[]) newArr;
    }
    public static int[] trim(int[] arr, int start, int end) {
        if (end <= start) {
            throw new IllegalArgumentException("End position at or before starting position");
        }
        int[] newArr = new int[end - start];
        for (int i = 0; i < end - start; i++) {
            newArr[i] = arr[i + start];
        }
        return newArr;
    }
    public static double[] trim(double[] arr, int start, int end) {
        if (end <= start) {
            throw new IllegalArgumentException("End position at or before starting position");
        }
        double[] newArr = new double[end - start];
        for (int i = 0; i < end - start; i++) {
            newArr[i] = arr[i + start];
        }
        return newArr;
    }
    public static double[] join(double[] arr1, double[] arr2) {
        double[] joinedArr = new double[arr1.length + arr2.length];
        for (int i = 0; i < arr1.length; i++) {
            joinedArr[i] = arr1[i];
        }
        for (int i = 0; i < arr2.length; i++) {
            joinedArr[arr1.length + i] = arr2[i];
        }
        return joinedArr;
    }
    public static int[] join(int[] arr1, int[] arr2) {
        int[] joinedArr = new int[arr1.length + arr2.length];
        for (int i = 0; i < arr1.length; i++) {
            joinedArr[i] = arr1[i];
        }
        for (int i = 0; i < arr2.length; i++) {
            joinedArr[arr1.length + i] = arr2[i];
        }
        return joinedArr;
    }
    public static <T> T[] join(T[] arr1, T[] arr2) {
        T[] joinedArr = (T[]) Array.newInstance(arr1.getClass().getComponentType(), arr1.length + arr2.length);
        for (int i = 0; i < arr1.length; i++) {
            joinedArr[i] = arr1[i];
        }
        for (int i = 0; i < arr2.length; i++) {
            joinedArr[arr1.length + i] = arr2[i];
        }
        return joinedArr;
    }
}
