package com.pakgopay.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * IP helper utilities.
 */
public class IpAddressUtil {

    /**
     * Resolve the first non-loopback IPv4 address of the current host.
     *
     * @return server IPv4 address, fallback to 127.0.0.1
     */
    public static String resolveServerIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            InetAddress local = InetAddress.getLocalHost();
            if (local != null) {
                return local.getHostAddress();
            }
        } catch (Exception e) {
            // ignore and fallback
        }
        return "127.0.0.1";
    }
}
