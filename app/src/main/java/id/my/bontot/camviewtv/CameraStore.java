package id.my.bontot.camviewtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Everything, including discovery metadata, is encrypted at rest with a device-bound key. */
public final class CameraStore {
    private static final String KEY_ALIAS = "camviewtv.cameras.v1";
    private final SharedPreferences prefs;
    public String loadError;

    public CameraStore(Context context) { prefs = context.getSharedPreferences("camera_vault", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public List<Camera> load() {
        loadError = null;
        List<Camera> result = new ArrayList<>();
        String stored = prefs.getString("data", null);
        if (stored == null) return result;
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Invalid vault format");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            JSONArray array = new JSONArray(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) result.add(Camera.fromJson(array.getJSONObject(i)));
        } catch (Exception error) {
            loadError = "Camera data could not be opened. Add your cameras and local camera credentials again on this TV.";
        }
        return result;
    }
    public boolean save(List<Camera> cameras) {
        try {
            JSONArray array = new JSONArray();
            for (Camera camera : cameras) array.put(camera.toJson());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
            String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(array.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            return prefs.edit().putString("data", encrypted).commit();
        } catch (Exception error) { return false; }
    }
}
