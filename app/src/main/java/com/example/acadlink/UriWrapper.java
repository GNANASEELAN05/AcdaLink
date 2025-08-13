package com.example.acadlink;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/**
 * Small helper to get name/size from a URI string safely.
 */
public class UriWrapper {
    enum UriMode { FROM_STRING, FROM_URI }

    private final Context ctx;
    private final Uri uri;

    public UriWrapper(Context ctx, UriMode mode, String uriString) {
        this.ctx = ctx;
        if (mode == UriMode.FROM_STRING) this.uri = Uri.parse(uriString);
        else this.uri = null;
    }

    public String getDisplayName() {
        if (uri == null) return null;
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        // fallback
        String path = uri.getPath();
        if (path != null) return path.substring(path.lastIndexOf('/') + 1);
        return uri.getLastPathSegment();
    }

    public long getSizeBytes() {
        if (uri == null) return -1;
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx != -1) return cursor.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
