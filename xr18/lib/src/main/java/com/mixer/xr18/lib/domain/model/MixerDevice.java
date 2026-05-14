package com.mixer.xr18.lib.domain.model;

/**
 * Represents a discovered XR18 mixer on the network.
 */
public class MixerDevice {
    public String ipAddress;
    public String name;
    public String model;
    public String firmwareVersion;
    public int port;

    public MixerDevice(String ipAddress, String name, String model, String firmwareVersion) {
        this(ipAddress, name, model, firmwareVersion, 10023);
    }

    public MixerDevice(String ipAddress, String name, String model, String firmwareVersion, int port) {
        this.ipAddress = ipAddress;
        this.name = name;
        this.model = model;
        this.firmwareVersion = firmwareVersion;
        this.port = port;
    }

    public String getIpAddress() { return ipAddress; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public int getPort() { return port; }
}