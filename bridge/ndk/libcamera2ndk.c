/* The camera NDK (libcamera2ndk.so) for apps that link it (Godot): there is no camera HAL, so every call reports an error and the camera list is empty.
 * ACAMERA_ERROR_UNKNOWN = -10000; ACameraManager_create is the one call that returns a pointer. */
#define ERR (-10000)
void *ACameraManager_create(void) { return 0; }
int ACameraCaptureSession_close(void) { return ERR; }
int ACameraCaptureSession_setRepeatingRequest(void) { return ERR; }
int ACameraCaptureSession_stopRepeating(void) { return ERR; }
int ACameraDevice_close(void) { return ERR; }
int ACameraDevice_createCaptureRequest(void) { return ERR; }
int ACameraDevice_createCaptureSession(void) { return ERR; }
int ACameraManager_delete(void) { return ERR; }
int ACameraManager_deleteCameraIdList(void) { return ERR; }
int ACameraManager_getCameraCharacteristics(void) { return ERR; }
int ACameraManager_getCameraIdList(void) { return ERR; }
int ACameraManager_openCamera(void) { return ERR; }
int ACameraMetadata_free(void) { return ERR; }
int ACameraMetadata_getConstEntry(void) { return ERR; }
int ACameraOutputTarget_create(void) { return ERR; }
int ACameraOutputTarget_free(void) { return ERR; }
int ACaptureRequest_addTarget(void) { return ERR; }
int ACaptureRequest_free(void) { return ERR; }
int ACaptureSessionOutputContainer_add(void) { return ERR; }
int ACaptureSessionOutputContainer_create(void) { return ERR; }
int ACaptureSessionOutputContainer_free(void) { return ERR; }
int ACaptureSessionOutput_create(void) { return ERR; }
int ACaptureSessionOutput_free(void) { return ERR; }
int ACameraManager_registerAvailabilityCallback(void) { return ERR; }
int ACameraManager_unregisterAvailabilityCallback(void) { return ERR; }
int ACameraMetadata_copy(void) { return ERR; }
int ACameraDevice_getId(void) { return ERR; }
int ACaptureRequest_setEntry_u8(void) { return ERR; }
int ACaptureRequest_setEntry_i32(void) { return ERR; }
int ACaptureRequest_setEntry_float(void) { return ERR; }
int ACameraCaptureSession_capture(void) { return ERR; }
int ACameraCaptureSession_abortCaptures(void) { return ERR; }
