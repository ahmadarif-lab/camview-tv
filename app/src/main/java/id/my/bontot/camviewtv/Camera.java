package id.my.bontot.camviewtv;

import android.net.Uri;
import org.json.JSONObject;

/** A LAN camera and its user-supplied local camera credentials. Never log this object as JSON. */
public final class Camera {
    public String ip, name, model = "", mac = "", onvifUrl = "";
    public int rtspPort = 554, onvifPort = 0;
    public boolean rtsp = false, onvif = false, favorite = false, userNamed = false;
    public String username = "", password = "", streamPath = "/stream1";
    public long lastSeen = 0;

    public Camera(String ip) { this.ip = ip; this.name = "Camera " + ip; }
    public boolean hasAccount() { return !username.isEmpty() && !password.isEmpty(); }
    public String rtspUri() {
        String account = username.isEmpty() ? "" : Uri.encode(username) + ":" + Uri.encode(password) + "@";
        String host = ip.contains(":") ? "[" + ip + "]" : ip;
        return "rtsp://" + account + host + ":" + rtspPort + streamPath;
    }
    public String services() {
        if (rtsp && onvif) return "RTSP · ONVIF";
        if (rtsp) return "RTSP";
        if (onvif) return "ONVIF";
        return "LAN camera";
    }
    public void mergeDiscovery(Camera found) {
        if (!userNamed && found.name != null && !found.name.isEmpty()) name = found.name;
        if (found.model != null && !found.model.isEmpty()) model = found.model;
        if (found.mac != null && !found.mac.isEmpty()) mac = found.mac;
        if (found.onvifUrl != null && !found.onvifUrl.isEmpty()) onvifUrl = found.onvifUrl;
        if (found.onvifPort > 0) onvifPort = found.onvifPort;
        if (!userNamed && found.rtsp && found.rtspPort > 0) rtspPort = found.rtspPort;
        // Preserve a user-edited RTSP port and path on repeated scans.
        rtsp |= found.rtsp;
        onvif |= found.onvif;
        lastSeen = System.currentTimeMillis();
    }
    JSONObject toJson() throws Exception {
        return new JSONObject().put("ip", ip).put("name", name).put("model", model).put("mac", mac)
            .put("onvifUrl", onvifUrl).put("rtspPort", rtspPort).put("onvifPort", onvifPort)
            .put("rtsp", rtsp).put("onvif", onvif).put("username", username).put("password", password)
            .put("streamPath", streamPath).put("favorite", favorite).put("userNamed", userNamed).put("lastSeen", lastSeen);
    }
    static Camera fromJson(JSONObject json) throws Exception {
        Camera c = new Camera(json.getString("ip"));
        c.name = json.optString("name", c.name); c.model = json.optString("model");
        if (c.name.equals("Kamera " + c.ip)) c.name = "Camera " + c.ip;
        c.mac = json.optString("mac"); c.onvifUrl = json.optString("onvifUrl");
        c.rtspPort = json.optInt("rtspPort", 554); c.onvifPort = json.optInt("onvifPort");
        c.rtsp = json.optBoolean("rtsp"); c.onvif = json.optBoolean("onvif");
        c.username = json.optString("username"); c.password = json.optString("password");
        c.streamPath = json.optString("streamPath", "/stream1");
        c.favorite = json.optBoolean("favorite"); c.userNamed = json.optBoolean("userNamed");
        c.lastSeen = json.optLong("lastSeen");
        return c;
    }
}
