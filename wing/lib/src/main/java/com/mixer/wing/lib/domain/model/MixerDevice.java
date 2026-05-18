package com.mixer.wing.lib.domain.model;

/**
 * Represents a discovered WING mixer on the network.
 */
public class MixerDevice {
    public String ipAddress;
    public String name;
    public String model;
    public String firmwareVersion;
    public String serial;
    public int port;

    public MixerDevice(String ipAddress, String name, String model, String firmwareVersion) {
        this(ipAddress, name, model, firmwareVersion, 10024);
    }

    public MixerDevice(String ipAddress, String name, String model, String firmwareVersion, int port) {
        this.ipAddress = ipAddress;
        this.name = name;
        this.model = model;
        this.firmwareVersion = firmwareVersion;
        this.serial = "";
        this.port = port;
    }

    public MixerDevice(String ipAddress, String name, String model, String firmwareVersion, String serial, int port) {
        this.ipAddress = ipAddress;
        this.name = name;
        this.model = model;
        this.firmwareVersion = firmwareVersion;
        this.serial = serial;
        this.port = port;
    }

    public String getIpAddress() { return ipAddress; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public String getSerial() { return serial; }
    public int getPort() { return port; }
}