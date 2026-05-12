package com.mixer.wing.util;

import com.mixer.wing.wapi.WApi;
import com.mixer.wing.wapi.WApiException;
import com.mixer.wing.wapi.WApiTokens;

/**
 * WING Channel Status Reader
 * 
 * Reads all channel parameters from WING console and formats as natural text.
 * 
 * Version: V1.0001
 */
public class WingStatusReader {
    
    public static final int MAX_CHANNELS = 48;
    public static final int MAX_BUSES = 16;
    
    private final WApi wapi;
    
    public WingStatusReader(WApi wapi) {
        this.wapi = wapi;
    }
    
    /**
     * Read and format all channel statuses
     * @return formatted status string
     */
    public String readAllChannels() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WING Channel Status ===\n");
        
        // Read input channels
        sb.append("\n--- INPUT CHANNELS ---\n");
        for (int ch = 1; ch <= MAX_CHANNELS; ch++) {
            try {
                String status = readChannelStatus(ch);
                if (status != null) {
                    sb.append(status).append("\n");
                }
            } catch (Exception e) {
                // Channel might not exist
            }
        }
        
        // Read buses
        sb.append("\n--- BUSES ---\n");
        for (int bus = 1; bus <= MAX_BUSES; bus++) {
            try {
                String status = readBusStatus(bus);
                if (status != null) {
                    sb.append(status).append("\n");
                }
            } catch (Exception e) {
                // Bus might not exist
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Read single channel status
     * @param ch channel number (1-48)
     * @return formatted status string
     */
    public String readChannelStatus(int ch) {
        try {
            float fader = wapi.getChannelFader(ch);
            boolean muted = wapi.getChannelMute(ch);
            float pan = wapi.getChannelPan(ch);
            float gain = wapi.getChannelGain(ch);
            
            String chName = "CH" + String.format("%02d", ch);
            String faderStr = WApi.faderToDisplay(fader);
            String muteStr = muted ? "ON 🔇" : "OFF";
            String panStr = WApi.panToDisplay(pan);
            String gainStr = String.format("%.1f dB", gain);
            
            return String.format("%s Fader: %s, Mute: %s, Pan: %s, Gain: %s",
                chName, faderStr, muteStr, panStr, gainStr);
                
        } catch (WApiException e) {
            return String.format("CH%02d: Error - %s", ch, e.getMessage());
        }
    }
    
    /**
     * Read single bus status
     * @param bus bus number (1-16)
     * @return formatted status string
     */
    public String readBusStatus(int bus) {
        try {
            float fader = wapi.getBusFader(bus);
            boolean muted = wapi.getBusMute(bus);
            
            String busName = "BUS" + String.format("%02d", bus);
            String faderStr = WApi.faderToDisplay(fader);
            String muteStr = muted ? "ON 🔇" : "OFF";
            
            return String.format("%s Fader: %s, Mute: %s",
                busName, faderStr, muteStr);
                
        } catch (WApiException e) {
            return String.format("BUS%02d: Error - %s", bus, e.getMessage());
        }
    }
    
    /**
     * Quick scan - just main channels (1-8) for quick status
     */
    public String quickScan() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WING Quick Scan (CH 1-8) ===\n");
        
        for (int ch = 1; ch <= 8; ch++) {
            try {
                float fader = wapi.getChannelFader(ch);
                boolean muted = wapi.getChannelMute(ch);
                
                String chName = "CH" + ch;
                String faderStr = WApi.faderToDisplay(fader);
                String muteStr = muted ? "M" : " ";
                
                sb.append(String.format("%s[%s] %s\n", chName, muteStr, faderStr));
                
            } catch (Exception e) {
                sb.append(String.format("CH%d: ---\n", ch));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Generate a compact one-line summary
     */
    public String compactSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("WING: ");
        
        int activeCount = 0;
        int mutedCount = 0;
        
        for (int ch = 1; ch <= 8; ch++) {
            try {
                boolean muted = wapi.getChannelMute(ch);
                if (!muted) activeCount++;
                else mutedCount++;
            } catch (Exception ignored) {}
        }
        
        sb.append(activeCount).append(" active, ").append(mutedCount).append(" muted");
        return sb.toString();
    }
}