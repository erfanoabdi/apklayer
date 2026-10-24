/*
 * Copyright 2016 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Mac.apk note (GMS-free adaptation, why this differs from the upstream class):
// This is the last of the Firebase C++ SDK's embedded com.google.firebase.app.
// internal.cpp.* helpers that the native firebase::util::Initialize FindClass-es
// (the others -- Log / CppThreadDispatcher / CppThreadDispatcherContext /
// JniResultCallback -- are provided verbatim). The upstream version depends on
// com.google.android.gms.common.GoogleApiAvailability + ConnectionResult to
// detect / install Google Play Services. macOS has NO Google Play Services and
// none can be installed, so we cannot (and need not) depend on GMS here. This
// variant keeps the EXACT JNI surface the C++ SDK binds against
// (makeGooglePlayServicesAvailable(Activity) + stopCallbacks() +
// RegisterNatives(onCompleteNative)) so FindClass + method lookup + native
// registration all succeed and firebase::App::Create completes, while reporting
// the platform truth: GMS is unavailable and cannot be made available, so the
// SDK takes its no-GMS path. This is the correct macOS behavior, not a stub that
// hides a bug. App-agnostic (any Firebase-C++ app), not per-APK code.

package com.google.firebase.app.internal.cpp;

import android.app.Activity;

/**
 * GMS-free replacement for the Firebase C++ SDK helper that checks whether
 * Google Play Services is available. On macOS, GMS is never available.
 */
public class GoogleApiAvailabilityHelper {
  /**
   * Upstream returns true if it STARTED an async attempt to make Play services
   * available (a later onCompleteNative reports the outcome), or false if it
   * could not even start (GMS unavailable / not resolvable). On macOS GMS is
   * unavailable and not resolvable, so we return false with no callback; the
   * C++ SDK then proceeds on its no-GMS path.
   */
  public static boolean makeGooglePlayServicesAvailable(Activity activity) {
    return false;
  }

  /** No async attempt is ever running here, so there is nothing to stop. */
  public static void stopCallbacks() {}

  // Registered by the Firebase C++ SDK via RegisterNatives. Never invoked here
  // (makeGooglePlayServicesAvailable returns false without scheduling work), but
  // declared so the SDK's RegisterNatives lookup succeeds.
  private static native void onCompleteNative(int resultCode, String resultMessage);
}
