package com.saksham.poker.server.lan;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds the addresses other computers on the local network can reach this one at. */
public final class NetworkInfo {

    private NetworkInfo() {
    }

    /**
     * This computer's IPv4 addresses on local networks, such as {@code 192.168.1.20}. Empty if it is
     * not connected to one.
     */
    public static List<String> lanAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return List.of();
        }
        return addresses;
    }

    /** The address players type into the desktop app, for each local network address. */
    public static List<String> serverUrls(int port, String contextPath) {
        List<String> urls = new ArrayList<>();
        for (String address : lanAddresses()) {
            urls.add("http://" + address + ":" + port + contextPath);
        }
        return urls;
    }
}
