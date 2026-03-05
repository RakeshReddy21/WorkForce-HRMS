package org.example.workforce.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetworkIpUtil {

    private static final Logger log = LoggerFactory.getLogger(NetworkIpUtil.class);

    private static volatile String cachedNetworkIp = null;
    private static volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 30_000;

    public static String getLocalNetworkIp() {
        long now = System.currentTimeMillis();
        if (cachedNetworkIp != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedNetworkIp;
        }

        String detectedIp = detectNetworkIp();
        if (detectedIp != null) {
            cachedNetworkIp = detectedIp;
            cacheTimestamp = now;
            log.info("[NetworkIpUtil] Network IP resolved: {}", detectedIp);
        } else {
            log.warn("[NetworkIpUtil] No suitable network IP found");
        }
        return detectedIp;
    }

    private static String detectNetworkIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;

            String wifiIp = null;
            String ethernetIp = null;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (ni.isLoopback() || !ni.isUp()) continue;

                String displayName = ni.getDisplayName().toLowerCase();
                String name = ni.getName().toLowerCase();

                if (isVirtualAdapter(ni, displayName, name)) {
                    log.debug("[NetworkIpUtil] SKIPPING virtual adapter: {} ({})", ni.getDisplayName(), name);
                    continue;
                }

                boolean isWifi = isWifiAdapter(displayName, name);

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();

                        if (isWifi && wifiIp == null) {
                            wifiIp = ip;
                            log.info("[NetworkIpUtil] WiFi IP detected: {} (interface: {})",
                                    ip, ni.getDisplayName());
                        } else if (!isWifi && ethernetIp == null) {
                            ethernetIp = ip;
                            log.info("[NetworkIpUtil] Ethernet IP detected: {} (interface: {})",
                                    ip, ni.getDisplayName());
                        }
                    }
                }
            }

            if (wifiIp != null) {
                return wifiIp;
            }
            return ethernetIp;

        } catch (SocketException e) {
            log.warn("[NetworkIpUtil] Failed to scan network interfaces: {}", e.getMessage());
        }

        return null;
    }

    private static boolean isVirtualAdapter(NetworkInterface ni, String displayNameLower, String nameLower) {

        if (ni.isVirtual()) return true;

        if (displayNameLower.contains("docker")) return true;

        if (displayNameLower.contains("vethernet")) return true;
        if (displayNameLower.contains("hyper-v")) return true;
        if (displayNameLower.contains("wsl")) return true;

        if (displayNameLower.contains("vmware")) return true;
        if (displayNameLower.contains("vmnet")) return true;

        if (displayNameLower.contains("virtualbox")) return true;
        if (displayNameLower.contains("vboxnet")) return true;

        if (displayNameLower.contains("virtual")) return true;

        if (displayNameLower.contains("tunnel")) return true;
        if (displayNameLower.contains("teredo")) return true;
        if (displayNameLower.contains("isatap")) return true;

        if (nameLower.startsWith("docker")) return true;
        if (nameLower.startsWith("veth")) return true;
        if (nameLower.startsWith("br-")) return true;

        if (nameLower.startsWith("virbr")) return true;

        return false;
    }

    private static boolean isWifiAdapter(String displayNameLower, String nameLower) {

        if (displayNameLower.contains("wi-fi")) return true;
        if (displayNameLower.contains("wifi")) return true;
        if (displayNameLower.contains("wireless")) return true;
        if (displayNameLower.contains("wlan")) return true;
        if (displayNameLower.contains("802.11")) return true;

        if (nameLower.startsWith("wlan")) return true;
        if (nameLower.startsWith("wlp")) return true;
        if (nameLower.equals("en0")) return true;

        return false;
    }

    public static boolean isLoopback(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String firstIp = xff.split(",")[0].trim();

            if (!isLoopback(firstIp)) {
                return firstIp;
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !isLoopback(xRealIp.trim())) {
            return xRealIp.trim();
        }

        if (isLoopback(remoteAddr)) {
            String networkIp = getLocalNetworkIp();
            if (networkIp != null) {
                log.debug("[NetworkIpUtil] Resolved loopback {} → WiFi/LAN IP {}", remoteAddr, networkIp);
                return networkIp;
            }
        }

        return remoteAddr;
    }

    public static String resolveLoopbackToLanIp(String ip) {
        if (ip == null) return "";
        if (isLoopback(ip)) {
            String lanIp = getLocalNetworkIp();
            return lanIp != null ? lanIp : ip;
        }
        return ip;
    }
}
