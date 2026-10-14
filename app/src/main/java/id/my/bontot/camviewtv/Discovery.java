package id.my.bontot.camviewtv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.util.Xml;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Local read-only discovery. Never submits credentials or changes camera settings. */
public final class Discovery {
    public interface Listener {
        void onCamera(Camera camera);
        void onProgress(String message);
        void onComplete(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Map<String, Camera> cameras = new LinkedHashMap<>();
    private final List<DatagramSocket> datagrams = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean cancelled;
    private volatile ExecutorService pool;
    private WifiManager.MulticastLock multicastLock;

    public Discovery(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        Thread thread = new Thread(this::run, "camera-discovery");
        thread.setDaemon(true);
        thread.start();
    }

    public void cancel() {
        cancelled = true;
        ExecutorService workers = pool;
        if (workers != null) workers.shutdownNow();
        synchronized (datagrams) {
            for (DatagramSocket socket : datagrams) socket.close();
        }
    }

    private void run() {
        int total = 0;
        boolean bounded = false;
        try {
            List<Lan> lans = interfaces();
            if (lans.isEmpty()) {
                complete("No local IPv4 network. Connect this TV to your home Wi-Fi or Ethernet.");
                return;
            }
            try {
                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("camviewtv-discovery");
                    multicastLock.setReferenceCounted(false);
                    multicastLock.acquire();
                }
            } catch (RuntimeException ignored) { /* TCP fallback remains usable. */ }
            pool = Executors.newFixedThreadPool(32);
            List<Future<?>> discoveryTasks = new ArrayList<>();
            List<String> hosts = new ArrayList<>();
            for (Lan lan : lans) {
                bounded |= lan.bounded;
                progress("Scanning " + lan.label + " · ONVIF, TP-Link and RTSP");
                discoveryTasks.add(pool.submit(() -> udpDiscovery(lan)));
                for (long host = lan.first + 1; host < lan.last; host++) {
                    if (host != lan.local) hosts.add(ip(host));
                }
            }
            total = hosts.size();
            int targetCount = total;
            AtomicInteger done = new AtomicInteger();
            List<Future<?>> probes = new ArrayList<>();
            for (String host : hosts) {
                if (cancelled) break;
                probes.add(pool.submit(() -> {
                    if (!cancelled) inspectHost(host);
                    int count = done.incrementAndGet();
                    if (count % 16 == 0 || count == targetCount)
                        progress("Scanning " + count + "/" + targetCount + " addresses · " + cameraCount() + " cameras");
                }));
            }
            for (Future<?> future : probes) {
                if (cancelled) break;
                try { future.get(); } catch (Exception ignored) { }
            }
            for (Future<?> future : discoveryTasks) {
                if (cancelled) break;
                try { future.get(6, TimeUnit.SECONDS); } catch (Exception ignored) { }
            }
            complete("Complete · " + cameraCount() + " cameras found · " + total + " addresses scanned"
                    + (bounded ? " (large subnet limited to this TV’s /22 block)" : ""));
        } catch (Exception e) {
            complete("Scan incomplete. Check the TV’s network and try again.");
        } finally {
            cancel();
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        }
    }

    private void inspectHost(String ip) {
        Camera camera = new Camera(ip);
        for (int port : new int[]{554, 8554}) {
            if (cancelled) return;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 380);
                socket.setSoTimeout(650);
                String req = "OPTIONS rtsp://" + ip + ":" + port + "/stream1 RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: CamViewTV\r\n\r\n";
                socket.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[2048];
                int n = socket.getInputStream().read(buffer);
                if (n > 0 && new String(buffer, 0, n, StandardCharsets.US_ASCII).startsWith("RTSP/")) {
                    camera.rtsp = true;
                    camera.rtspPort = port;
                    publish(camera);
                    break;
                }
            } catch (Exception ignored) { }
        }
        // Confirm ONVIF by its SOAP namespace, including an authentication fault.
        // An arbitrary open web port is never labelled a camera.
        for (int port : new int[]{2020, 8899, 8000, 80, 8080}) {
            if (cancelled) return;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 250);
            } catch (Exception ignored) { continue; }
            String endpoint = "http://" + ip + ":" + port + "/onvif/device_service";
            Map<String, String> metadata = onvifInfo(endpoint);
            if (!metadata.isEmpty()) {
                camera.onvif = true;
                camera.onvifPort = port;
                camera.onvifUrl = endpoint;
                camera.model = metadata.getOrDefault("Model", "");
                if (!camera.model.isEmpty()) camera.name = camera.model;
                publish(camera);
                break;
            }
        }
    }

    private Map<String, String> onvifInfo(String endpoint) {
        Map<String, String> result = new LinkedHashMap<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(500);
            conn.setReadTimeout(700);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8; action=\"http://www.onvif.org/ver10/device/wsdl/GetDeviceInformation\"");
            byte[] body = ("<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body>"
                    + "<tds:GetDeviceInformation xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"/>"
                    + "</s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            conn.getOutputStream().write(body);
            int code = conn.getResponseCode();
            InputStream input = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (input == null) return result;
            String xml;
            try (InputStream stream = input) { xml = readBounded(stream, 32768); }
            if (!xml.contains("http://www.onvif.org/")) return result;
            result.put("ONVIF", "true");
            result.putAll(xmlFields(xml, "Model", "Manufacturer"));
        } catch (Exception ignored) { }
        finally { if (conn != null) conn.disconnect(); }
        return result;
    }

    private void udpDiscovery(Lan lan) {
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(ip(lan.local), 0))) {
            datagrams.add(socket);
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            byte[] tp = new byte[]{2, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0x46, 0x3c, (byte) 0xb5, (byte) 0xd3};
            byte[] legacy = xor("{\"system\":{\"get_sysinfo\":{}}}");
            byte[] ws = ("<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" "
                    + "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "
                    + "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">"
                    + "<s:Header><a:MessageID>uuid:" + UUID.randomUUID() + "</a:MessageID>"
                    + "<a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>"
                    + "<a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>"
                    + "</s:Header><s:Body><d:Probe/></s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 2 && !cancelled; i++) {
                send(socket, tp, ip(lan.last), 20002);
                send(socket, legacy, ip(lan.last), 9999);
                send(socket, ws, "239.255.255.250", 3702);
            }
            long deadline = System.currentTimeMillis() + 4500;
            while (!cancelled && System.currentTimeMillis() < deadline) {
                try {
                    byte[] buffer = new byte[32768];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    long source = numeric(packet.getAddress().getAddress());
                    if (source <= lan.first || source >= lan.last) continue;
                    String host = packet.getAddress().getHostAddress();
                    Camera camera = new Camera(host);
                    if (packet.getPort() == 20002 || packet.getPort() == 20004) {
                        if (packet.getLength() <= 16) continue;
                        JSONObject object = new JSONObject(new String(buffer, 16, packet.getLength() - 16, StandardCharsets.UTF_8));
                        JSONObject result = object.optJSONObject("result");
                        if (result == null) continue;
                        String type = result.optString("device_type", "").toLowerCase(java.util.Locale.ROOT);
                        // Discovery also sees plugs and hubs; only retain actual camera families.
                        if (!type.contains("camera") && !type.contains("ipcamera") && !type.contains("nvr")) continue;
                        camera.model = result.optString("device_model", "");
                        camera.mac = result.optString("mac", "");
                        camera.name = camera.model.isEmpty() ? "TP-Link camera" : camera.model;
                        publish(camera);
                    } else if (packet.getPort() != 9999) {
                        String xml = new String(buffer, 0, packet.getLength(), StandardCharsets.UTF_8);
                        if (!xml.contains("ProbeMatch")) continue;
                        Map<String, String> info = xmlFields(xml, "Types", "Scopes", "XAddrs");
                        String types = info.getOrDefault("Types", "").toLowerCase(java.util.Locale.ROOT);
                        String scopes = info.getOrDefault("Scopes", "");
                        if (!types.contains("networkvideo") && !scopes.contains("onvif://")) continue;
                        camera.onvif = true;
                        for (String addr : info.getOrDefault("XAddrs", "").trim().split("\\s+")) {
                            try {
                                URI uri = new URI(addr);
                                // Only accept the responder's local HTTP endpoint.
                                if (host.equals(uri.getHost()) && "http".equals(uri.getScheme())) {
                                    camera.onvifUrl = addr;
                                    camera.onvifPort = uri.getPort() < 0 ? 80 : uri.getPort();
                                    break;
                                }
                            } catch (Exception ignored) { }
                        }
                        for (String scope : scopes.split("\\s+")) {
                            if (scope.contains("/name/")) camera.name = android.net.Uri.decode(scope.substring(scope.indexOf("/name/") + 6));
                            if (scope.contains("/hardware/")) camera.model = android.net.Uri.decode(scope.substring(scope.indexOf("/hardware/") + 10));
                        }
                        publish(camera);
                    }
                } catch (java.net.SocketTimeoutException ignored) { }
                catch (Exception ignored) { if (socket.isClosed()) break; }
            }
            datagrams.remove(socket);
        } catch (Exception ignored) { }
    }

    private synchronized void publish(Camera hint) {
        if (cancelled) return;
        Camera known = cameras.get(hint.ip);
        if (known == null) known = new Camera(hint.ip);
        if (hint.model != null && !hint.model.isEmpty()) known.model = hint.model;
        if (hint.name != null && !hint.name.isEmpty() && !hint.name.equals("Camera " + hint.ip)) known.name = hint.name;
        if (hint.mac != null && !hint.mac.isEmpty()) known.mac = hint.mac;
        if (hint.rtsp) { known.rtsp = true; known.rtspPort = hint.rtspPort; }
        if (hint.onvif) { known.onvif = true; known.onvifPort = hint.onvifPort; known.onvifUrl = hint.onvifUrl; }
        known.lastSeen = System.currentTimeMillis();
        cameras.put(known.ip, known);
        // Callback receives a snapshot so queued UI work never sees concurrent mutation.
        Camera snapshot = new Camera(known.ip);
        snapshot.name = known.name;
        snapshot.model = known.model;
        snapshot.mac = known.mac;
        snapshot.rtsp = known.rtsp;
        snapshot.rtspPort = known.rtspPort;
        snapshot.onvif = known.onvif;
        snapshot.onvifPort = known.onvifPort;
        snapshot.onvifUrl = known.onvifUrl;
        snapshot.lastSeen = known.lastSeen;
        listener.onCamera(snapshot);
    }

    private synchronized int cameraCount() { return cameras.size(); }
    private void progress(String message) { if (!cancelled) listener.onProgress(message); }
    private void complete(String message) { if (!cancelled) listener.onComplete(message); }

    private List<Lan> interfaces() throws Exception {
        List<Lan> result = new ArrayList<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities cap = cm.getNetworkCapabilities(network);
                if (cap == null || (!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && !cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
                LinkProperties properties = cm.getLinkProperties(network);
                if (properties == null) continue;
                for (LinkAddress address : properties.getLinkAddresses()) {
                    addLan(result, address.getAddress(), address.getPrefixLength());
                }
            }
        }
        if (result.isEmpty()) {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.getName().startsWith("tun")) continue;
                for (java.net.InterfaceAddress addr : nic.getInterfaceAddresses())
                    addLan(result, addr.getAddress(), addr.getNetworkPrefixLength());
            }
        }
        return result;
    }

    private static void addLan(List<Lan> lans, InetAddress address, int prefix) {
        if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress() || prefix < 1 || prefix > 30) return;
        Lan lan = new Lan(numeric(address.getAddress()), prefix);
        for (Lan existing : lans) if (existing.first == lan.first && existing.last == lan.last) return;
        lans.add(lan);
    }

    static final class Lan {
        final long local, first, last;
        final boolean bounded;
        final String label;
        Lan(long local, int prefix) {
            this.local = local;
            bounded = prefix < 22;
            int scannedPrefix = Math.max(22, prefix);
            long mask = (0xffffffffL << (32 - scannedPrefix)) & 0xffffffffL;
            first = local & mask;
            last = first | (~mask & 0xffffffffL);
            label = ip(first) + "/" + scannedPrefix;
        }
    }

    private static void send(DatagramSocket socket, byte[] bytes, String ip, int port) {
        try { socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(ip), port)); }
        catch (Exception ignored) { }
    }

    private static byte[] xor(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        int key = 171;
        for (int i = 0; i < bytes.length; i++) { key = key ^ (bytes[i] & 255); bytes[i] = (byte) key; }
        return bytes;
    }

    private static Map<String, String> xmlFields(String xml, String... wanted) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) return result;
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event != XmlPullParser.START_TAG) continue;
            for (String key : wanted) {
                if (key.equals(parser.getName())) { result.put(key, parser.nextText()); break; }
            }
        }
        return result;
    }

    private static String readBounded(InputStream input, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int n;
        while (out.size() < max && (n = input.read(buffer, 0, Math.min(buffer.length, max - out.size()))) > 0) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }

    private static long numeric(byte[] a) { return ((a[0] & 255L) << 24) | ((a[1] & 255L) << 16) | ((a[2] & 255L) << 8) | (a[3] & 255L); }
    private static String ip(long n) { return ((n >> 24) & 255) + "." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255); }
}
