/* The camera NDK against the real libcamera2ndk: ids, LENS_FACING, SENSOR_ORIENTATION, open, a repeating YUV request into an AImageReader. */
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCaptureRequest.h>
#include <media/NdkImageReader.h>

static void on_disc(void *c, ACameraDevice *d) { (void)c; (void)d; }
static void on_err(void *c, ACameraDevice *d, int e) { (void)c; (void)d; printf("[ndkcam] device error %d\n", e); }
static void s_closed(void *c, ACameraCaptureSession *s) { (void)c; (void)s; }
static void s_ready(void *c, ACameraCaptureSession *s) { (void)c; (void)s; }
static void s_active(void *c, ACameraCaptureSession *s) { (void)c; (void)s; }

static const char *check(char *buf, const char *what, int status) {
    if (status == ACAMERA_OK) return 0;
    snprintf(buf, 200, "%s: %d", what, status);
    return buf;
}

static const char *session(ACameraDevice *dev, char *buf) {
    AImageReader *reader = 0;
    if (AImageReader_new(640, 480, AIMAGE_FORMAT_YUV_420_888, 2, &reader) != AMEDIA_OK) return "AImageReader_new";
    ANativeWindow *window = 0;
    AImageReader_getWindow(reader, &window);
    ACaptureSessionOutputContainer *container;
    ACaptureSessionOutput *output;
    ACaptureSessionOutputContainer_create(&container);
    ACaptureSessionOutput_create(window, &output);
    ACaptureSessionOutputContainer_add(container, output);
    ACameraCaptureSession_stateCallbacks cb = {0, s_closed, s_ready, s_active};
    ACameraCaptureSession *session = 0;
    const char *err = check(buf, "createCaptureSession", ACameraDevice_createCaptureSession(dev, container, &cb, &session));
    ACaptureRequest *request = 0;
    ACameraOutputTarget *target = 0;
    if (!err) err = check(buf, "createCaptureRequest", ACameraDevice_createCaptureRequest(dev, TEMPLATE_PREVIEW, &request));
    if (!err) { ACameraOutputTarget_create(window, &target); ACaptureRequest_addTarget(request, target); }
    if (!err) err = check(buf, "setRepeatingRequest", ACameraCaptureSession_setRepeatingRequest(session, 0, 1, &request, 0));
    int w = 0, h = 0;
    for (int i = 0; !err && i < 100 && !w; i++) {  // up to 10 s for the first frame
        AImage *image = 0;
        if (AImageReader_acquireLatestImage(reader, &image) == AMEDIA_OK && image) {
            AImage_getWidth(image, &w); AImage_getHeight(image, &h);
            uint8_t *y = 0; int len = 0;
            AImage_getPlaneData(image, 0, &y, &len);
            printf("[ndkcam] frame %dx%d plane0 %d bytes, y[0]=%d\n", w, h, len, y ? y[0] : -1);
            AImage_delete(image);
        } else usleep(100000);
    }
    if (!err && !w) err = "no frame";
    if (session) { ACameraCaptureSession_stopRepeating(session); ACameraCaptureSession_close(session); }
    if (target) ACameraOutputTarget_free(target);
    if (request) ACaptureRequest_free(request);
    ACaptureSessionOutputContainer_free(container);
    ACaptureSessionOutput_free(output);
    AImageReader_delete(reader);
    return err;
}

JNIEXPORT jstring JNICALL Java_hello_cameranative_Main_run(JNIEnv *env, jclass cls) {
    static char buf[256], msg[300];
    const char *err = 0;
    ACameraManager *manager = ACameraManager_create();
    ACameraIdList *list = 0;
    if (!manager) err = "ACameraManager_create";
    for (int i = 0; manager && i < 100; i++) {  // cameraserver starts on the first lookup and tells its cameras by callback: the first lists can be empty
        if (list) ACameraManager_deleteCameraIdList(list);
        list = 0;
        err = check(buf, "getCameraIdList", ACameraManager_getCameraIdList(manager, &list));
        if (err || list->numCameras > 0) { printf("[ndkcam] list after %d ms\n", i * 100); break; }
        usleep(100000);
    }
    if (!err && list->numCameras < 1) err = "empty camera list";
    for (int i = 0; !err && i < list->numCameras; i++) {
        const char *id = list->cameraIds[i];
        ACameraMetadata *meta = 0;
        err = check(buf, "getCameraCharacteristics", ACameraManager_getCameraCharacteristics(manager, id, &meta));
        if (err) break;
        ACameraMetadata_const_entry facing = {0}, orientation = {0};
        ACameraMetadata_getConstEntry(meta, ACAMERA_LENS_FACING, &facing);
        ACameraMetadata_getConstEntry(meta, ACAMERA_SENSOR_ORIENTATION, &orientation);
        printf("[ndkcam] camera %s facing %d (0 front, 1 back) orientation %d\n", id, facing.count ? facing.data.u8[0] : -1, orientation.count ? orientation.data.i32[0] : -1);
        if (!facing.count) err = "no LENS_FACING";
        ACameraMetadata_free(meta);
        if (err || i) continue;  // the frame check on the first camera
        ACameraDevice_stateCallbacks dcb = {0, on_disc, on_err};
        ACameraDevice *dev = 0;
        err = check(buf, "openCamera", ACameraManager_openCamera(manager, id, &dcb, &dev));
        if (err) break;
        printf("[ndkcam] opened %s\n", ACameraDevice_getId(dev));
        err = session(dev, buf);
        ACameraDevice_close(dev);
    }
    if (list) ACameraManager_deleteCameraIdList(list);
    if (manager) ACameraManager_delete(manager);
    snprintf(msg, sizeof msg, "%s", err ? err : "");
    return (*env)->NewStringUTF(env, msg);
}
