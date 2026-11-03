package apklayer.host;

import android.accounts.Account;
import android.os.Bundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The instance's accounts, what AccountManagerService keeps in accounts_ce.db: per account its password, user data and cached auth tokens,
 * in one JSON file of the instance (written whole on every change, through a temporary file).
 */
final class HubAccountStore {
    static final class Entry {
        String password;
        final Map<String, String> data = new LinkedHashMap<>(), tokens = new LinkedHashMap<>();
    }

    private final Path file;
    private final Map<Account, Entry> accounts = new LinkedHashMap<>();

    HubAccountStore(Path file) {
        this.file = file;
        try {
            if (!Files.isRegularFile(file)) return;
            JSONArray list = new JSONObject(Files.readString(file)).getJSONArray("accounts");
            for (int i = 0; i < list.length(); i++) {
                JSONObject a = list.getJSONObject(i);
                Entry entry = new Entry();
                entry.password = a.isNull("password") ? null : a.getString("password");
                read(a.getJSONObject("data"), entry.data);
                read(a.getJSONObject("tokens"), entry.tokens);
                accounts.put(new Account(a.getString("name"), a.getString("type")), entry);
            }
        } catch (IOException | JSONException unreadable) {
            System.out.println("[apklayer-hub] accounts: cannot read " + file + " (" + unreadable + "): starting empty");
        }
    }

    synchronized Account[] list(String type) { return accounts.keySet().stream().filter(a -> type == null || a.type.equals(type)).toArray(Account[]::new); }
    synchronized boolean has(Account account) { return account != null && accounts.containsKey(account); }

    /** A copy of one field of the account's entry (null when there is no such account). */
    synchronized <T> T read(Account account, java.util.function.Function<Entry, T> field) {
        Entry entry = account == null ? null : accounts.get(account);
        return entry == null ? null : field.apply(entry);
    }

    synchronized boolean add(Account account, String password, Bundle extras) {
        if (account == null || accounts.containsKey(account)) return false;
        Entry entry = new Entry();
        entry.password = password;
        if (extras != null) for (String key : extras.keySet()) if (extras.get(key) instanceof String value) entry.data.put(key, value);
        accounts.put(account, entry);
        save();
        return true;
    }

    synchronized boolean remove(Account account) {
        if (account == null || accounts.remove(account) == null) return false;
        save();
        return true;
    }

    synchronized Account rename(Account account, String name) {
        Entry entry = account == null ? null : accounts.get(account);
        Account renamed = entry == null ? null : new Account(name, account.type);
        if (renamed == null || accounts.containsKey(renamed)) return null;
        Map<Account, Entry> copy = new LinkedHashMap<>(accounts);
        accounts.clear();
        copy.forEach((a, e) -> accounts.put(a.equals(account) ? renamed : a, e));  // keeps the order
        save();
        return renamed;
    }

    /** Changes one account's entry; false when there is no such account. */
    synchronized boolean update(Account account, Consumer<Entry> change) {
        Entry entry = account == null ? null : accounts.get(account);
        if (entry == null) return false;
        change.accept(entry);
        save();
        return true;
    }

    /** Forgets the token wherever an account of the type caches it (AccountManager.invalidateAuthToken). */
    synchronized void invalidate(String type, String token) {
        boolean changed = false;
        for (Map.Entry<Account, Entry> e : accounts.entrySet())
            if (e.getKey().type.equals(type)) changed |= e.getValue().tokens.values().removeIf(token::equals);
        if (changed) save();
    }

    private void save() {
        try {
            JSONArray list = new JSONArray();
            for (Map.Entry<Account, Entry> e : accounts.entrySet())
                list.put(new JSONObject().put("name", e.getKey().name).put("type", e.getKey().type).put("password", e.getValue().password == null ? JSONObject.NULL : e.getValue().password)
                        .put("data", new JSONObject(e.getValue().data)).put("tokens", new JSONObject(e.getValue().tokens)));
            Files.createDirectories(file.getParent());
            Path part = file.resolveSibling(file.getFileName() + ".part");
            Files.writeString(part, new JSONObject().put("accounts", list).toString(1), StandardCharsets.UTF_8);
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | JSONException failure) {
            throw new IllegalStateException("Cannot write the accounts to " + file, failure);
        }
    }

    private static void read(JSONObject object, Map<String, String> into) throws JSONException {
        for (java.util.Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            into.put(key, object.getString(key));
        }
    }
}
