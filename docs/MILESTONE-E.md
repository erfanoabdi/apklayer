# Milestone E: apps talk to each other (instance hub), plus a real com.android.webview provider

The user's full work list (2026-10-27), the order of work for the assistant. Each step ends with automated cross-app tests on the VM, a commit, and updated PLAN/memory.
The hub manages provider selection, packages and renderer-process lifecycle; WebView's Java/native implementation loads inside each consuming app, its renderer runs
separately, each app keeps its own cookies and storage.

## Status
- **E1 (done, step 1a)**: real `android.os.Binder`/`BinderProxy`/`Parcel` JNI in the app JVM behind `-Dapklayer.realbinder=true`; `RealBinder.publish/lookup`; two-JVM test
  `tests/vm/binder.sh` (published binder, call, callback, caller identities, death on `kill -9`). media, player, audio and gpu regressions pass with the flag on.
- **E1 services via the hub (done by the other session)**: `tests/vm/hub-services.sh [bound|auto]`.
- **E2 broadcasts, first part (done 2026-10-27)**: `tests/vm/broadcasts.sh [manifest|basic|ordered|sticky]`: the hub plays the activity manager's part (`HubBroadcasts`); Android's own ContextImpl,
  LoadedApk.ReceiverDispatcher, ActivityThread.handleReceiver and BroadcastReceiver.PendingResult run in the apps, ending in a hub-backed `IActivityManager` (registerReceiver,
  broadcastIntent, finishReceiver, unbroadcastIntent). Verified: manifest receiver of a package that was not running (hub starts it headless), dynamic receivers across apps,
  exported / not-exported rules, receiver and sender permissions, ordered delivery by priority across manifest and dynamic receivers with result propagation, abort and the final
  result receiver, sticky intents (BROADCAST_STICKY, delivered to a later registration). Patches: `ShadowBroadcastPendingResult` and `ShadowBroadcastReceiver` overlays (Robolectric's
  stand-ins never reached the activity manager / dropped the abort). Also verified: goAsync across apps (the ordered broadcast waits for the late finish), unregisterReceiver inside onReceive, receiver-process death (registrations gone, manifest receiver restarts the package). Open in E2: system sticky seeds (battery) for apps on the hub route.
- Next: E3 content providers.

## Work list
1. **Package and identity model of the hub**: installed packages, components, signatures, permissions, enabled state; install/update/removal and stale metadata; process
   registrations tied to host-authorized launches and endpoint lifetimes; permission checks beyond the normal ones; the shared host-UID limitation stays explicit
   (full instance isolation is separate work).
2. **Service lifecycle** (extend E1): several services/bindings on one ServiceConnection; bindings separated by `Intent.FilterComparison`; onUnbind/onRebind, reference
   counting, cancellation; binding API variants and Executor delivery; client death, server crashes, failed startup, package replacement; keep the generated-AIDL,
   death/reconnect and cleanup tests.
3. **Broadcasts (E2)**: dynamic registration/unregistration; manifest receivers incl. starting a non-running app; Android's intent-filter matching and exported rules;
   sender/receiver permissions; ordered delivery, priorities, result propagation, abort; PendingResult/goAsync; sticky broadcasts and queries; cleanup on unregister/death.
4. **Content providers and observers (E3)**: authorities, lazy provider process start; real IContentProvider over Binder; query/insert/update/delete/batch/call/getType;
   cursors, cursor windows, file descriptors; clients, cancellation, provider death; observers and notifyChange; exported, read/write and URI permissions; small provider
   APKs first (Settings/Contacts and the other system providers are milestone F).
5. **Cross-app activities and results (E4)**: explicit and implicit intent resolution from installed manifests; full native Parcel payloads (Parcelable and Binder extras);
   launch or reuse the app process; onNewIntent, results, cancellation; essential task/back-stack flags and lifecycle; focus, windows, background-to-foreground; replace the
   string-only intent/result files.
6. **Started and foreground services**: cross-app startService/stopService/foreground start; start ids, stopSelf, bound-plus-started lifetime; restart/redelivery;
   foreground-service notification; permission and background-execution restrictions.
7. **Pending intents and URI grants**: hub-owned real IIntentSender tokens; activity/broadcast/service pending intents; creator identity, cancel, update, one-shot,
   mutability; fill-in intents, completion callbacks; URI grants through intents and pending intents; behaviour after the creator exits.
8. **Notifications (E5)**: per-package channels, post/update/cancel; the desktop notification bridge on hub-owned records; content/delete/action pending intents; replies
   and foreground-service notifications; permissions and package ownership.
9. **Validate E as a whole**: multi-APK tests over all routes; crash, restart, cancellation, repeated launch; unrelated apps cannot reach private components; recheck media,
   GPU rendering, input, cleanup; enable the hub route by default only after that. CTS stays last.
10. **WebView feasibility, early** (alongside E): pick a standalone com.android.webview APK matching our framework and ABI; convert and load its Java code, dependencies
    and resources; load its native library through bionic_translation; audit JNI registration, class-loader assumptions, dynamic code loading, ART dependencies; decide
    whether thin patches suffice. Provider eligibility: package, version/SDK, native-library metadata; a regular Chromium browser APK is not enough.
11. **WebView provider installation and selection**: install as an instance system package; package info, metadata, resources, native library paths; the framework-facing
    WebView update-service contract; validate and select one provider per instance; missing/disabled/updated/incompatible handling; Trichrome shared-library packaging
    only if chosen later (standalone first).
12. **Original WebView implementation in apps**: WebViewFactory and provider creation; provider class loader and resource context; native library load and init; RELRO/shared
    memory behaviour; replace the WebKitGTK shadow for this selectable provider route; creation and destruction of several WebViews.
13. **Hub support for WebView renderer services**: isolated/external service bindings and named instances; provider code in the consuming app's context; init Binders, file
    descriptors, shared memory; renderer death, disconnect, rebind, cleanup; meaningful renderer isolation (same-UID hosts do not justify claiming Chromium's sandbox);
    process-priority and lifecycle hooks.
14. **Chromium on the native graphics route**: WebView drawing into HWUI and our EGL/gralloc/SurfaceFlinger stack (functors, surfaces, buffer ownership); synchronisation,
    resize, clipping, scrolling, transparency; touch, keyboard, IME, focus, accessibility; measure real GPU work and copies before claiming zero-copy.
15. **WebView app-facing behaviour**: navigation, history, settings, callbacks; JavaScript evaluation and interfaces; cookies, cache, DOM storage, per-app separation;
    HTTP/TLS, redirects, errors, interception; assets, content:// and URI permissions; file chooser, downloads, permission prompts; media, fullscreen, pop-ups, renderer-crash
    callbacks; AndroidX WebKit features the provider supports.
16. **Validate and maintain the provider**: two unrelated apps using WebView at once; separate cookies/storage and renderer ownership; renderer crashes, app exits, provider
    updates; representative WebView-based apps; record gaps, keep the provider updateable; WebKitGTK stays as an optional fallback while the APK route matures.

Execution order: finish E2-E5 and the service gaps while doing the early WebView feasibility check; then provider loading, isolated renderers, graphics and WebView
compatibility. Installing the APK alone does not provide the missing runtime integration.

## E3 status (2026-10-27)
- Content providers across apps: tests/vm/providers.sh basic|denied|death pass (query/insert/update/delete/getType/call over the provider's real IContentProvider, cursor windows over the framework's real CursorWindow natives, lazy provider start, private provider refused, read/write permissions, restart after SIGKILL).
- Known limitation: every app JVM has the same Linux uid (no zygote setuid), so Binder.getCallingUid() cannot tell apps apart; permission checks use the hub's pid-to-package map (RemoteProvider enforces read/write, HubPermissionChecker serves "permission_checker"). Real fix: a distinct Linux uid per package (userns/subuid or privileged launcher + data dir ownership). TODO.
- Still open in E3: observers/notifyChange across apps, URI permission grants, batches/FDs.
- Observers (done): notifyChange in one app reaches the other apps' observers through the hub (ShadowHostContentResolver, NOTIFY_CHANGE → ENDPOINT_NOTIFY); providers.sh observe passes. Open in E3: URI permission grants, batches/FDs.
- Files/batches (done): openInputStream over a real file descriptor and applyBatch across apps (providers.sh files; ParcelFileDescriptor runs its own code under the real binder). Gap: ParcelFileDescriptor.createPipe/socketpair return null (libcore Os not backed): tracked for E-wide validation. Open in E3: URI permission grants.
