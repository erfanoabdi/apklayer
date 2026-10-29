package hello.system;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.util.Set;

/** MediaProvider: an image goes into MediaStore.Images with a relative path, its bytes are written through the provider, the row is found with its size, then removed. */
final class MediaCheck implements Check {
    // a 1x1 PNG
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1f, 0x15, (byte) 0xc4, (byte) 0x89, 0, 0, 0, 13, 'I', 'D', 'A', 'T',
            0x78, (byte) 0x9c, 0x63, (byte) 0xf8, (byte) 0xcf, (byte) 0xc0, (byte) 0xf0, 0x1f, 0, 5, 0, 1, (byte) 0xff, (byte) 0x89, (byte) 0x99, 0x3d, 0x1d, 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82};

    @Override public String run(Context context) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Set<String> volumes = MediaStore.getExternalVolumeNames(context);
        Check.require(volumes.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY), "volumes " + volumes);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "apklayer-check.png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/apklayer");
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Check.require(uri != null, "no uri");
        try (OutputStream out = resolver.openOutputStream(uri)) { out.write(PNG); }
        long size = -1;
        for (int i = 0; i < 40 && size != PNG.length; i++) {  // the provider scans the file after the writer closed it
            try (Cursor c = resolver.query(uri, new String[] {MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DISPLAY_NAME}, null, null, null)) {
                Check.require(c != null && c.moveToFirst(), "the row is not there");
                size = c.getLong(0);
            }
            if (size != PNG.length) Thread.sleep(250);
        }
        Check.require(size == PNG.length, "size " + size + " instead of " + PNG.length);
        int count;
        try (Cursor c = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Images.Media._ID}, MediaStore.Images.Media.DISPLAY_NAME + "=?", new String[] {"apklayer-check.png"}, null)) {
            count = c == null ? -1 : c.getCount();
        }
        Check.require(count == 1, "rows by name: " + count);
        resolver.delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentUris.parseId(uri)), null, null);
        return uri + " " + size + " bytes, volumes " + volumes;
    }
}
