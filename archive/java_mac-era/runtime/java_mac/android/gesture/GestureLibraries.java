// android.gesture.GestureLibraries -- the four factories that give an app a
// GestureLibrary: from a path, a File, a ParcelFileDescriptor, an app-private
// file, or a raw resource. Mirrors AOSP
// frameworks/base/core/java/android/gesture/GestureLibraries.java verbatim
// (aosp-mirror android16-release, fetched 2026-08-24).
//
// fromRawResource() is the one that matters most in practice: the standard
// idiom is to ship res/raw/gestures produced by the Gesture Builder app and
// load it at startup. It routes through Resources.openRawResource, which
// Mac.apk implements, so a shipped gesture library really does get learned.
package android.gesture;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import static android.gesture.GestureConstants.LOG_TAG;

public final class GestureLibraries {
    private GestureLibraries() {
    }

    public static GestureLibrary fromFile(String path) {
        return fromFile(new File(path));
    }

    public static GestureLibrary fromFile(File path) {
        return new FileGestureLibrary(path);
    }

    public static GestureLibrary fromFileDescriptor(ParcelFileDescriptor pfd) {
        return new FileGestureLibrary(pfd.getFileDescriptor());
    }

    public static GestureLibrary fromPrivateFile(Context context, String name) {
        return fromFile(context.getFileStreamPath(name));
    }

    public static GestureLibrary fromRawResource(Context context, int resourceId) {
        return new ResourceGestureLibrary(context, resourceId);
    }

    private static class FileGestureLibrary extends GestureLibrary {
        // Either a file or an fd is used
        private final File mPath;
        private final FileDescriptor mFd;

        public FileGestureLibrary(File path) {
            mPath = path;
            mFd = null;
        }

        public FileGestureLibrary(FileDescriptor fd) {
            mPath = null;
            mFd = fd;
        }

        /**
         * If this GestureLibrary was created using a FileDescriptor, this
         * method will always return false.
         */
        @Override
        public boolean isReadOnly() {
            if (mPath != null) {
                return !mPath.canWrite();
            }
            return false;
        }

        public boolean save() {
            if (!mStore.hasChanged()) return true;
            boolean result = false;

            if (mPath != null) {
                final File file = mPath;

                final File parentFile = file.getParentFile();
                if (parentFile != null && !parentFile.exists()) {
                    if (!parentFile.mkdirs()) {
                        return false;
                    }
                }

                try {
                    file.createNewFile();
                    mStore.save(new FileOutputStream(file), true);
                    result = true;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Could not save the gesture library in " + mPath, e);
                }
            } else {
                try {
                    mStore.save(new FileOutputStream(mFd), true);
                    result = true;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Could not save the gesture library", e);
                }
            }
            return result;
        }

        public boolean load() {
            boolean result = false;
            if (mPath != null) {
                final File file = mPath;
                if (file.exists() && file.canRead()) {
                    try {
                        mStore.load(new FileInputStream(file), true);
                        result = true;
                    } catch (IOException e) {
                        Log.d(LOG_TAG, "Could not load the gesture library from " + mPath, e);
                    }
                }
            } else {
                try {
                    mStore.load(new FileInputStream(mFd), true);
                    result = true;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Could not load the gesture library", e);
                }
            }

            return result;
        }
    }

    private static class ResourceGestureLibrary extends GestureLibrary {
        private final WeakReference<Context> mContext;
        private final int mResourceId;

        public ResourceGestureLibrary(Context context, int resourceId) {
            mContext = new WeakReference<Context>(context);
            mResourceId = resourceId;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        public boolean save() {
            return false;
        }

        public boolean load() {
            boolean result = false;
            final Context context = mContext.get();
            if (context != null) {
                final InputStream in = context.getResources().openRawResource(mResourceId);
                try {
                    mStore.load(in, true);
                    result = true;
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Could not load the gesture library from raw resource "
                            + context.getResources().getResourceName(mResourceId), e);
                }
            }

            return result;
        }
    }
}
