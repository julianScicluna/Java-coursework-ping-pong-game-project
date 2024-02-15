public class TimeUtils {
    public static String toFormattedTime(long t) {
        if (t < 0) {
            throw new IllegalArgumentException("The time to format cannot be below zero");
        }
        //For very small operations using a String instead of a StringBuilder shoudn't create noticeable performance problems. Besides, modern, optimised JVM versions use StringBuilder internally when doing string concatenation
        String str = "";
        if ((t / 86400) != 0) {
            str += (t / 86400) + ":";
        }
        if (((t / 3600) % 86400) != 0) {
            str += ((t / 3600) % 24) + ":";
        }
        str += JSsubstr("0" + ((t / 60) % 60), -2) + ":" +  JSsubstr("0" + (t % 60), -2);
        return str;
    }

    private static String JSsubstr(String str, int from, int to) {
        if (from >= 0) {
            if (to >= 0) {
                return str.substring(from, to);
            } else {
                return str.substring(from, str.length() + to);
            }
        } else {
            if (to >= 0) {
                return str.substring(str.length() + from, to);
            } else {
                return str.substring(str.length() + from, str.length() + to);
            }
        }
    }

    private static String JSsubstr(String str, int from) {
        if (from >= 0) {
            return str.substring(from);
        } else {
            return str.substring(str.length() + from);
        }
    }
}