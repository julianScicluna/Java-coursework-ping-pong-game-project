package Helpers;

public class StringHelpers {
    //Constructor to prevent this utility class from being instaniated
    private StringHelpers() {
        throw new UnsupportedOperationException("Cannot instantiate StringHelpers utility class");
    }
    /**
     * A method to search a string for a desired target and return its index. Returns {@code -1} if the specified string does not exist in the specified string
     * @param str The string in which to search for the target string
     * @param target The string to look for
     * @return An {@code int} representing the index of the first character of the target string within the string to search within or {@code -1} if the target string cannot be found
     */
    public static int indexOf(String str, String target) {
        //If the target string is longer than the string to search in, it would be impossible for it to contain the target string. Save the CPU the hassle and return -1 there and then
        if (target.length() > str.length()) {
            return -1;
        }
        char[] stringCharArr = str.toCharArray(), targetCharArr = target.toCharArray();
        int numCharsEqual = 0;
        for (int i = 0; i < stringCharArr.length; i++) {
            if (stringCharArr[i] == targetCharArr[numCharsEqual]) {
                numCharsEqual++;
            } else {
                numCharsEqual = 0;
            }
            if (numCharsEqual == targetCharArr.length) {
                return i - targetCharArr.length;
            }
        }
        return -1;
    }

    /**
     * A method to search a string for a desired target and return its index. Returns {@code -1} if the specified string does not exist in the specified string
     * @param str The string in which to search for the target string
     * @param target The string to look for
     * @param start The index to start searching at
     * @param end The index at which to stop searching
     * @return An {@code int} representing the index of the first character of the target string within the string to search within or {@code -1} if the target string cannot be found
     */
    public static int indexOf(String str, String target, int start, int end) {
        //Handle a number of cases which would otherwise have caused confusing results or java.lang.ArrayIndexOutOfBoundsExceptions
        if (end < start) {
            throw new IllegalArgumentException("Invalid search bounds - end before start");
        } else if (start < 0) {
            throw new IllegalArgumentException("Invalid search bounds - start before the first string character");
        } else if (end > str.length()) {
            throw new IllegalArgumentException("Invalid search bounds - end after last string character");
        } else if (target.length() > end - start) {
            //If the target string is longer than the string to search in, it would be impossible for it to contain the target string. Save the CPU the hassle and return -1 there and then
            return -1;
        }
        char[] stringCharArr = str.toCharArray(), targetCharArr = target.toCharArray();
        int numCharsEqual = 0;
        for (int i = start; i < end; i++) {
            if (stringCharArr[i] == targetCharArr[numCharsEqual]) {
                numCharsEqual++;
            } else {
                numCharsEqual = 0;
            }
            if (numCharsEqual == targetCharArr.length) {
                return i - targetCharArr.length;
            }
        }
        return -1;
    }

    /**
     * A method to search a string for a desired target and return its index. Returns {@code -1} if the specified string does not exist in the specified string
     * @param str The string in which to search for the target string
     * @param target The string to look for
     * @param start The index to start searching at
     * @return An {@code int} representing the index of the first character of the target string within the string to search within or {@code -1} if the target string cannot be found
     */
    public static int indexOf(String str, String target, int start) {
        //Handle a number of cases which would otherwise have caused confusing results or java.lang.ArrayIndexOutOfBoundsExceptions
        if (start < 0) {
            throw new IllegalArgumentException("Invalid search bounds - start before the first string character");
        } else if (target.length() > str.length() - start) {
            //If the target string is longer than the string to search in, it would be impossible for it to contain the target string. Save the CPU the hassle and return -1 there and then
            return -1;
        }
        char[] stringCharArr = str.toCharArray(), targetCharArr = target.toCharArray();
        int numCharsEqual = 0;
        for (int i = start; i < stringCharArr.length; i++) {
            if (stringCharArr[i] == targetCharArr[numCharsEqual]) {
                numCharsEqual++;
            } else {
                numCharsEqual = 0;
            }
            if (numCharsEqual == targetCharArr.length) {
                return i - targetCharArr.length;
            }
        }
        return -1;
    }

    /**
     * A method to search a string for a desired number of targets and return whether any of the targets exist in the string to search. Returns {@code true} if any of the targets exist in the string, {@code false} otherwise
     * @param str The string in which to search for the target string
     * @param targets A list of string to search for within {@code str}
     * @return A {@code boolean}, {@code true} if any of the targets exist in the string or {@code false} otherwise
     */
    public static boolean indexOf(String str, String[] targets) {
        //If the target string is longer than the string to search in, it would be impossible for it to contain the target string. Save the CPU the hassle and return -1 there and then
        char[] stringCharArr = str.toCharArray();
        char[][] targetCharArr = new char[targets.length][];
        for (int i = 0; i < targetCharArr.length; i++) {
            targetCharArr[i] = targets[i].toCharArray();
        }
        for (int i = 0; i < targets.length; i++) {
            int numCharsEqual = 0;
            for (int j = 0; j < stringCharArr.length; j++) {
                if (stringCharArr[j] == targetCharArr[i][numCharsEqual]) {
                    numCharsEqual++;
                } else {
                    numCharsEqual = 0;
                }
                if (numCharsEqual == targetCharArr[i].length) {
                    return true;
                }
            }
        }
        return false;
    }
}
