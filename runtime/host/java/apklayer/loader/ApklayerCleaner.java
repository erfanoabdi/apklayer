package apklayer.loader;

/**
 * libcore's sun.misc.Cleaner, which HotSpot's JDK no longer has (it is jdk.internal.ref.Cleaner there). AppClassLoader defines this class under the name
 * sun.misc.Cleaner for an app that asks for it (netty, Okio, direct-buffer helpers). shortcut: not a PhantomReference, the JDK's java.lang.ref.Cleaner runs the
 * thunk once, on clean() or when the object is collected; upgrade when an app subclasses or queues the Cleaner itself.
 */
public final class ApklayerCleaner {
    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();
    private final java.lang.ref.Cleaner.Cleanable cleanable;

    private ApklayerCleaner(Object object, Runnable thunk) { cleanable = CLEANER.register(object, thunk); }

    public static ApklayerCleaner create(Object object, Runnable thunk) { return thunk == null ? null : new ApklayerCleaner(object, thunk); }

    public void clean() { cleanable.clean(); }
}
