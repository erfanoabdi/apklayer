package apklayer.host;

import java.util.List;
import java.util.Map;
import org.robolectric.annotation.Implements;

/**
 * The framework's camera classes (camera2's CameraManager and CameraDeviceImpl, android.hardware.Camera, their JNI in libandroid_runtime)
 * and the real ImageReader (libmedia_jni) over the stack's cameraserver and camera provider (aosp/hals/camera), used instead of
 * Robolectric's in-memory fakes when this stack declares the camera HAL (providers.toml, cli/stack.cpp): HostShadowProvider maps these then.
 * Each shadow is empty: the original code runs, natives included.
 */
final class HostCameraShadows {
    private HostCameraShadows() {}

    /** Whether this stack runs cameraserver over a camera provider: cli/stack.cpp declares the HAL (providers.toml's camera domain) and starts
     *  cameraserver when its library is staged. Both, or CameraManager would find no "media.camera". */
    static boolean declared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && (new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-camera.xml").exists() || deviceCamera(root))
                && new java.io.File(System.getProperty("apklayer.androidlibs", ""), "libapklayer_cameraserver.so").exists();
    }

    /** providers.toml gives the camera to the device (a Halium phone's own HIDL provider through the host hwbinder context: host_hals.conf lists it). */
    private static boolean deviceCamera(String root) {
        try {
            return new String(java.nio.file.Files.readAllBytes(new java.io.File(System.getenv("APKLAYER_DATA"), "apklayer/host_hals.conf").toPath())).contains("android.hardware.camera.provider\n");        } catch (java.io.IOException none) { return false; }
    }

    /** A window of this shape now. The instance's display is portrait-natural, so a portrait window reports rotation 0 and the app turns the
     *  preview by the sensor orientation; the backend's cameras stay upright on the landscape desktop (rotation 90, bridge/hals/camera camera.h).
     *  Android's camera compat info, which WindowManager sets for a fixed-orientation activity on a rotated display, makes cameraserver turn
     *  the frames back (ANDROID_SCALER_ROTATE_AND_CROP_90 for a back camera, 270 for a front one, aosp/hals/camera). Read at each camera open.
     *  A device camera (Halium) turns with the phone: none. */
    static void windowShape(boolean portrait) {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        if (root != null && deviceCamera(root)) return;
        android.content.res.CompatibilityInfo.setCameraCompatibilityInfo(new android.content.res.CameraCompatibilityInfo.Builder()
                .setRotateAndCropRotation(portrait ? android.view.Surface.ROTATION_90 : android.app.WindowConfiguration.ROTATION_UNDEFINED).build());
    }

    static List<Map.Entry<String, String>> entries() {
        return List.of(
                Map.entry("android.hardware.camera2.CameraManager", CameraManager.class.getName()),
                Map.entry("android.hardware.camera2.CameraManager.CameraManagerGlobal", CameraManagerGlobal.class.getName()),
                Map.entry("android.hardware.camera2.CameraCharacteristics", CameraCharacteristics.class.getName()),
                Map.entry("android.hardware.camera2.CameraMetadata", CameraMetadata.class.getName()),
                Map.entry("android.hardware.camera2.CaptureResult", CaptureResult.class.getName()),
                Map.entry("android.hardware.camera2.TotalCaptureResult", TotalCaptureResult.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraDeviceImpl", CameraDeviceImpl.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraCaptureSessionImpl", CameraCaptureSessionImpl.class.getName()),
                Map.entry("android.hardware.camera2.utils.SurfaceUtils", SurfaceUtils.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraMetadataNative", CameraMetadataNative.class.getName()),
                Map.entry("android.hardware.Camera", Camera.class.getName()),
                Map.entry("android.hardware.Camera.Parameters", CameraParameters.class.getName()),
                Map.entry("android.media.ImageReader", ImageReader.class.getName()),
                Map.entry("android.media.PublicFormatUtils", PublicFormatUtils.class.getName()),
                Map.entry("android.media.ImageReader.SurfaceImage", SurfaceImage.class.getName()));
    }

    // Robolectric keys a shadow by its @Implements className: one empty class per framework class
    @Implements(className = "android.hardware.camera2.CameraManager", isInAndroidSdk = false) public static final class CameraManager {}
    @Implements(className = "android.hardware.camera2.CameraManager$CameraManagerGlobal", isInAndroidSdk = false) public static final class CameraManagerGlobal {}
    @Implements(className = "android.hardware.camera2.CameraCharacteristics", isInAndroidSdk = false) public static final class CameraCharacteristics {}
    @Implements(className = "android.hardware.camera2.CameraMetadata", isInAndroidSdk = false) public static final class CameraMetadata {}
    @Implements(className = "android.hardware.camera2.CaptureResult", isInAndroidSdk = false) public static final class CaptureResult {}
    @Implements(className = "android.hardware.camera2.TotalCaptureResult", isInAndroidSdk = false) public static final class TotalCaptureResult {}
    @Implements(className = "android.hardware.camera2.impl.CameraDeviceImpl", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class CameraDeviceImpl {}
    @Implements(className = "android.hardware.camera2.impl.CameraCaptureSessionImpl", isInAndroidSdk = false) public static final class CameraCaptureSessionImpl {}
    @Implements(className = "android.hardware.camera2.utils.SurfaceUtils", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class SurfaceUtils {}
    @Implements(className = "android.hardware.camera2.impl.CameraMetadataNative", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class CameraMetadataNative {}
    @Implements(className = "android.hardware.Camera", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class Camera {}
    @Implements(className = "android.hardware.Camera$Parameters", isInAndroidSdk = false) public static final class CameraParameters {}
    @Implements(className = "android.media.PublicFormatUtils", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class PublicFormatUtils {}
    @Implements(className = "android.media.ImageReader", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class ImageReader {}
    @Implements(className = "android.media.ImageReader$SurfaceImage", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class SurfaceImage {}
}
