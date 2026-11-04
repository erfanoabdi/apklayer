package apklayer.host;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowBluetoothGatt;

/**
 * BluetoothGatt as a client of BlueZ's GATT objects (HostBluetooth): connect, the resolved services, characteristic and descriptor reads and writes,
 * notifications. Callbacks run on the session's reader thread, as the framework runs them on a binder thread when the app gave no handler.
 * The Client Characteristic Configuration descriptor is BlueZ's to write (StartNotify does it): writing it completes here.
 */
@Implements(className = "android.bluetooth.BluetoothGatt", isInAndroidSdk = false)
public class ShadowHostBluetoothGatt extends ShadowBluetoothGatt {
    private static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String[] FLAGS = {"broadcast", "read", "write-without-response", "write", "notify", "indicate", "authenticated-signed-writes", "extended-properties"};
    @RealObject private BluetoothGatt real;
    private String address;
    private BluetoothGattCallback callback;
    private Consumer<JSONObject> events;
    private volatile boolean connected;
    private final List<BluetoothGattService> services = new ArrayList<>();
    private final Map<String, Object> byPath = new HashMap<>();
    private final Map<Object, String> paths = new HashMap<>();

    /** A BluetoothGatt of this release's constructor (it changes between releases; Robolectric's newInstance knows older ones), its arguments filled by type. */
    static BluetoothGatt create(android.bluetooth.BluetoothDevice device, BluetoothGattCallback callback) {
        java.lang.reflect.Constructor<?> constructor = BluetoothGatt.class.getDeclaredConstructors()[0];
        Object[] args = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        try {
            for (int i = 0; i < args.length; i++) {
                Class<?> t = types[i];
                if (t == android.bluetooth.BluetoothDevice.class) args[i] = device;
                else if (t == BluetoothGattCallback.class) args[i] = callback;
                else if (t == int.class) args[i] = 0;
                else if (t == boolean.class) args[i] = false;
                else if (t == java.util.concurrent.Executor.class) args[i] = (java.util.concurrent.Executor) Runnable::run;
                else if (t == android.content.AttributionSource.class) args[i] = org.robolectric.RuntimeEnvironment.getApplication().getAttributionSource();
                else if (t.isInterface()) args[i] = org.robolectric.util.ReflectionHelpers.createNullProxy(t);
                else if (t.getName().endsWith("ConnectionSettings")) { Object builder = Class.forName(t.getName() + "$Builder").getConstructor().newInstance(); args[i] = builder.getClass().getMethod("build").invoke(builder); }
            }
            constructor.setAccessible(true);
            return (BluetoothGatt) constructor.newInstance(args);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot make a BluetoothGatt", failure); }
    }

    void open(String address, BluetoothGattCallback callback) {
        this.address = address;
        this.callback = callback;
        setGattCallback(callback);
        HostBluetooth session = HostBluetooth.get();
        if (session == null) {
            HostBluetooth.onMain(() -> callback.onConnectionStateChange(real, BluetoothGatt.GATT_FAILURE, BluetoothProfile.STATE_DISCONNECTED));
            return;
        }
        events = this::event;
        session.listen(events);
        session.send("op", "connect", "address", address);
    }

    private void event(JSONObject event) {
        String path = event.optString("path"), kind = event.optString("ev");
        Object target;
        synchronized (this) { target = byPath.get(path); }
        if (event.has("address") ? !address.equalsIgnoreCase(event.optString("address")) : target == null) return;  // another connection's
        int status = event.optInt("status", BluetoothGatt.GATT_SUCCESS);
        byte[] value = HostBluetooth.hex(event.optString("value", ""));
        switch (kind) {
            case "conn" -> {
                boolean now = event.optBoolean("connected");
                if (now == connected && status == BluetoothGatt.GATT_SUCCESS) return;
                connected = now;
                callback.onConnectionStateChange(real, status, now ? BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED);
            }
            case "services" -> { build(event.optJSONArray("services")); callback.onServicesDiscovered(real, BluetoothGatt.GATT_SUCCESS); }
            case "read" -> {
                if (target instanceof BluetoothGattCharacteristic c) { c.setValue(value); callback.onCharacteristicRead(real, c, value, status); }
                else if (target instanceof BluetoothGattDescriptor d) { d.setValue(value); callback.onDescriptorRead(real, d, status, value); }
            }
            case "write" -> {
                if (target instanceof BluetoothGattCharacteristic c) callback.onCharacteristicWrite(real, c, status);
                else if (target instanceof BluetoothGattDescriptor d) callback.onDescriptorWrite(real, d, status);
            }
            case "changed" -> { if (target instanceof BluetoothGattCharacteristic c) { c.setValue(value); callback.onCharacteristicChanged(real, c, value); } }
            default -> { }
        }
    }

    private synchronized void build(JSONArray tree) {
        services.clear(); byPath.clear(); paths.clear();
        for (int s = 0; tree != null && s < tree.length(); s++) {
            JSONObject service = tree.optJSONObject(s);
            BluetoothGattService gs = new BluetoothGattService(UUID.fromString(service.optString("uuid")),
                    service.optBoolean("primary", true) ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY);
            remember(service.optString("path"), gs);
            JSONArray chars = service.optJSONArray("chars");
            for (int c = 0; chars != null && c < chars.length(); c++) {
                JSONObject ch = chars.optJSONObject(c);
                int properties = 0;
                JSONArray flags = ch.optJSONArray("flags");
                for (int f = 0; flags != null && f < flags.length(); f++) properties |= 1 << java.util.Arrays.asList(FLAGS).indexOf(flags.optString(f)) & 0xff;
                int permissions = ((properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? BluetoothGattCharacteristic.PERMISSION_READ : 0)
                        | ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0 ? BluetoothGattCharacteristic.PERMISSION_WRITE : 0);
                BluetoothGattCharacteristic gc = new BluetoothGattCharacteristic(UUID.fromString(ch.optString("uuid")), properties, permissions);
                gs.addCharacteristic(gc);
                remember(ch.optString("path"), gc);
                JSONArray descs = ch.optJSONArray("descs");
                for (int d = 0; descs != null && d < descs.length(); d++) {
                    BluetoothGattDescriptor gd = new BluetoothGattDescriptor(UUID.fromString(descs.optJSONObject(d).optString("uuid")),
                            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
                    gc.addDescriptor(gd);
                    remember(descs.optJSONObject(d).optString("path"), gd);
                }
            }
            services.add(gs);
        }
    }

    private void remember(String path, Object object) { byPath.put(path, object); paths.put(object, path); }

    private synchronized String path(Object attribute) { return paths.get(attribute); }

    private boolean send(Object attribute, Object... command) {
        HostBluetooth session = HostBluetooth.get();
        String path = path(attribute);
        if (session == null || path == null || !connected) return false;
        Object[] full = new Object[command.length + 2];
        full[0] = "path"; full[1] = path;
        System.arraycopy(command, 0, full, 2, command.length);
        session.send(full);
        return true;
    }

    @Implementation @Override protected boolean connect() {
        HostBluetooth session = HostBluetooth.get();
        if (session == null || callback == null) return false;
        session.send("op", "connect", "address", address);
        return true;
    }

    @Implementation @Override protected void disconnect() {
        HostBluetooth session = HostBluetooth.get();
        if (session != null && address != null) session.send("op", "disconnect", "address", address);
    }

    @Implementation @Override protected void close() {
        HostBluetooth session = HostBluetooth.get();
        if (session != null && events != null) { session.unlisten(events); if (connected) session.send("op", "disconnect", "address", address); }
        events = null;
        connected = false;
    }

    @Implementation @Override protected boolean discoverServices() {
        HostBluetooth session = HostBluetooth.get();
        if (session == null || !connected) return false;
        session.send("op", "services", "address", address);
        return true;
    }

    @Implementation @Override protected synchronized List<BluetoothGattService> getServices() { return new ArrayList<>(services); }

    @Implementation @Override protected synchronized BluetoothGattService getService(UUID uuid) {
        return services.stream().filter(service -> service.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    @Implementation @Override protected boolean readCharacteristic(BluetoothGattCharacteristic c) {
        return (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0 && send(c, "op", "read");
    }

    @Implementation @Override protected boolean writeCharacteristic(BluetoothGattCharacteristic c) { return write(c, c.getValue(), c.getWriteType()); }

    @Implementation @Override protected int writeCharacteristic(BluetoothGattCharacteristic c, byte[] value, int writeType) {
        c.setValue(value);
        return write(c, value, writeType) ? BluetoothStatusCodes.SUCCESS : BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED;
    }

    private boolean write(BluetoothGattCharacteristic c, byte[] value, int writeType) {
        if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) return false;
        return send(c, "op", "write", "value", HostBluetooth.hex(value), "type", writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE ? "command" : "request");
    }

    @Implementation @Override protected boolean setCharacteristicNotification(BluetoothGattCharacteristic c, boolean enable) {
        return (c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0 && send(c, "op", "notify", "on", enable);
    }

    @Implementation protected boolean readDescriptor(BluetoothGattDescriptor d) { return send(d, "op", "read"); }

    @Implementation @Override protected boolean writeDescriptor(BluetoothGattDescriptor d) { return writeDescriptor(d, d.getValue()) == BluetoothStatusCodes.SUCCESS; }

    @Implementation @Override protected int writeDescriptor(BluetoothGattDescriptor d, byte[] value) {
        d.setValue(value);
        if (CCC.equals(d.getUuid()) && path(d) != null && connected) {  // BlueZ refuses writes to the CCC: setCharacteristicNotification already subscribed
            HostBluetooth.onMain(() -> callback.onDescriptorWrite(real, d, BluetoothGatt.GATT_SUCCESS));
            return BluetoothStatusCodes.SUCCESS;
        }
        return send(d, "op", "write", "value", HostBluetooth.hex(value)) ? BluetoothStatusCodes.SUCCESS : BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED;
    }

    // shortcut: BlueZ negotiates the ATT MTU itself; the default 23 is reported (safe for every write); upgrade by reading BlueZ's MTU property of the characteristics
    @Implementation @Override protected boolean requestMtu(int mtu) {
        if (!connected) return false;
        HostBluetooth.onMain(() -> callback.onMtuChanged(real, 23, BluetoothGatt.GATT_SUCCESS));
        return true;
    }
}
