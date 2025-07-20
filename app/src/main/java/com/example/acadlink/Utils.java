// File: Utils.java
package com.example.acadlink;

import android.content.Context;
import android.widget.Toast;

public class Utils {

    // 🔹 Simple toast (short duration)
    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    // 🔹 Long-duration toast (optional utility)
    public static void toastLong(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    // 🔹 Timestamp utility
    public static long getTimestamp() {
        return System.currentTimeMillis();
    }

    // 🔹 (Theme-related code removed as per request)
}
