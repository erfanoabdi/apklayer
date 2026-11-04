package apklayer.host;

import java.util.List;
import java.util.Map;
import org.robolectric.annotation.Implements;

/**
 * The framework's camera classes (camera2's CameraManager and CameraDeviceImpl, android.hardware.Camera, their JNI in libandroid_runtime)
 * and the real ImageReader (libmedia_jni) over the stack's cameraserver and camera provider (aosp/hals/camera), used instead of
 * Robolectric's in-memory fakes when this stack declares the camera HAL (cli/stack.cpp, APKLAYER_CAMERA): HostShadowProvider maps these then.
 * Each shadow is empty: the original code runs, natives included.
 */
final class HostCameraShadows {
    private HostCameraShadows() {}

    /** Whether this stack runs cameraserver over a camera provider: cli/stack.cpp declares the HAL when it starts both. */
    static boolean declared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-camera.xml").exists();
    }

    static List<Map.Entry<String, String>> entries() {
        return List.of(
                Map.entry("android.hardware.camera2.CameraManager", Plain.class.getName()),
                Map.entry("android.hardware.camera2.CameraManager.CameraManagerGlobal", ManagerGlobal.class.getName()),
                Map.entry("android.hardware.camera2.CameraCharacteristics", Plain.class.getName()),
                Map.entry("android.hardware.camera2.CameraMetadata", Plain.class.getName()),
                Map.entry("android.hardware.camera2.CaptureResult", Plain.class.getName()),
                Map.entry("android.hardware.camera2.TotalCaptureResult", Plain.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraDeviceImpl", Plain.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraCaptureSessionImpl", Plain.class.getName()),
                Map.entry("android.hardware.camera2.impl.CameraMetadataNative", Native.class.getName()),
                Map.entry("android.hardware.Camera", Native.class.getName()),
                Map.entry("android.hardware.Camera.Parameters", Plain.class.getName()),
                Map.entry("android.media.ImageReader", Native.class.getName()),
                Map.entry("android.media.ImageReader.SurfaceImage", Native.class.getName()));
    }

    // One shadow class serves several framework classes: Robolectric only needs it to exist and say how natives run.
    @Implements(className = "android.hardware.camera2.CameraManager", isInAndroidSdk = false) public static final class Plain {}
    @Implements(className = "android.hardware.camera2.CameraManager$CameraManagerGlobal", isInAndroidSdk = false) public static final class ManagerGlobal {}
    @Implements(className = "android.hardware.Camera", callNativeMethodsByDefault = true, isInAndroidSdk = false) public static final class Native {}
}
