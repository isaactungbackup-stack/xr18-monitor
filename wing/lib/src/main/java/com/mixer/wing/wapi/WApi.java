package com.mixer.wing.wapi;

import java.util.HashMap;
import java.util.Map;

/**
 * WING wapi High-Level Interface
 * 
 * Provides convenient high-level methods for interacting with WING parameters.
 * Combines WApiConnection with token-based parameter access.
 * 
 * Version: V1.0001
 */
public class WApi {
    
    private final WApiConnection conn;
    private final Map<String, Float> cache;
    private boolean cacheEnabled;
    
    /**
     * Create WApi instance
     * @param host WING console IP address
     * @param port TCP port (default 2222)
     */
    public WApi(String host, int port) {
        this.conn = new WApiConnection(host, port);
        this.cache = new HashMap<>();
        this.cacheEnabled = false;
    }
    
    public WApi(String host) {
        this(host, WApiConnection.DEFAULT_PORT);
    }
    
    // ============================================================
    // Connection lifecycle
    // ============================================================
    
    /**
     * Connect to WING console
     * @return version string
     * @throws WApiException on error
     */
    public String open() throws WApiException {
        return conn.open();
    }
    
    /**
     * Disconnect from WING console
     */
    public void close() {
        conn.close();
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return conn.isConnected();
    }
    
    /**
     * Enable/disable parameter caching
     */
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        if (!enabled) {
            cache.clear();
        }
    }
    
    // ============================================================
    // Channel parameters
    // ============================================================
    
    /**
     * Get channel fader value
     * @param ch channel number (1-48)
     * @return fader value (0.0 to 1.0, where 1.0 = +12dB)
     * @throws WApiException on error
     */
    public float getChannelFader(int ch) throws WApiException {
        int token = WApiTokens.chMixFader(ch);
        String key = "ch" + ch + "_fader";
        
        if (cacheEnabled && cache.containsKey(key)) {
            return cache.get(key);
        }
        
        float val = conn.getFloat(token);
        if (cacheEnabled) cache.put(key, val);
        return val;
    }
    
    /**
     * Set channel fader value
     * @param ch channel number (1-48)
     * @param value fader value (0.0 to 1.0)
     * @throws WApiException on error
     */
    public void setChannelFader(int ch, float value) throws WApiException {
        int token = WApiTokens.chMixFader(ch);
        conn.setFloat(token, value);
        if (cacheEnabled) cache.put("ch" + ch + "_fader", value);
    }
    
    /**
     * Get channel mute state
     * @param ch channel number (1-48)
     * @return true if muted
     * @throws WApiException on error
     */
    public boolean getChannelMute(int ch) throws WApiException {
        int token = WApiTokens.chMixOn(ch);
        return conn.getInt(token) == 0;  // Inverted: 0 = on, 1 = muted
    }
    
    /**
     * Set channel mute state
     * @param ch channel number (1-48)
     * @param muted true to mute
     * @throws WApiException on error
     */
    public void setChannelMute(int ch, boolean muted) throws WApiException {
        int token = WApiTokens.chMixOn(ch);
        conn.setInt(token, muted ? 1 : 0);
    }
    
    /**
     * Get channel pan value
     * @param ch channel number (1-48)
     * @return pan value (-1.0 = full left, 0.0 = center, 1.0 = full right)
     * @throws WApiException on error
     */
    public float getChannelPan(int ch) throws WApiException {
        int token = WApiTokens.chPan(ch);
        String key = "ch" + ch + "_pan";
        
        if (cacheEnabled && cache.containsKey(key)) {
            return cache.get(key);
        }
        
        float val = conn.getFloat(token);
        if (cacheEnabled) cache.put(key, val);
        return val;
    }
    
    /**
     * Get channel preamp gain
     * @param ch channel number (1-48)
     * @return gain in dB (typical range: -12 to +12)
     * @throws WApiException on error
     */
    public float getChannelGain(int ch) throws WApiException {
        int token = WApiTokens.chHaGain(ch);
        String key = "ch" + ch + "_gain";
        
        if (cacheEnabled && cache.containsKey(key)) {
            return cache.get(key);
        }
        
        float val = conn.getFloat(token);
        if (cacheEnabled) cache.put(key, val);
        return val;
    }
    
    /**
     * Set channel preamp gain
     * @param ch channel number (1-48)
     * @param gain gain in dB
     * @throws WApiException on error
     */
    public void setChannelGain(int ch, float gain) throws WApiException {
        int token = WApiTokens.chHaGain(ch);
        conn.setFloat(token, gain);
        if (cacheEnabled) cache.put("ch" + ch + "_gain", gain);
    }
    
    /**
     * Get channel EQ band gain
     * @param ch channel number (1-48)
     * @param band EQ band (1-4)
     * @return EQ gain in dB
     * @throws WApiException on error
     */
    public float getChannelEqGain(int ch, int band) throws WApiException {
        int token = WApiTokens.chEqGain(ch, band);
        String key = "ch" + ch + "_eq" + band + "_gain";
        
        if (cacheEnabled && cache.containsKey(key)) {
            return cache.get(key);
        }
        
        float val = conn.getFloat(token);
        if (cacheEnabled) cache.put(key, val);
        return val;
    }
    
    // ============================================================
    // Bus parameters
    // ============================================================
    
    /**
     * Get bus fader value
     * @param bus bus number (1-16)
     * @return fader value
     * @throws WApiException on error
     */
    public float getBusFader(int bus) throws WApiException {
        int token = WApiTokens.busMixFader(bus);
        return conn.getFloat(token);
    }
    
    /**
     * Get bus mute state
     * @param bus bus number (1-16)
     * @return true if muted
     * @throws WApiException on error
     */
    public boolean getBusMute(int bus) throws WApiException {
        int token = WApiTokens.busMixOn(bus);
        return conn.getInt(token) == 0;
    }
    
    // ============================================================
    // Raw token access
    // ============================================================
    
    /**
     * Get float value by raw token
     * @param token 32-bit token
     * @return float value
     * @throws WApiException on error
     */
    public float getRawFloat(int token) throws WApiException {
        return conn.getFloat(token);
    }
    
    /**
     * Set float value by raw token
     * @param token 32-bit token
     * @param value float value
     * @throws WApiException on error
     */
    public void setRawFloat(int token, float value) throws WApiException {
        conn.setFloat(token, value);
    }
    
    /**
     * Get int value by raw token
     * @param token 32-bit token
     * @return int value
     * @throws WApiException on error
     */
    public int getRawInt(int token) throws WApiException {
        return conn.getInt(token);
    }
    
    /**
     * Set int value by raw token
     * @param token 32-bit token
     * @param value int value
     * @throws WApiException on error
     */
    public void setRawInt(int token, int value) throws WApiException {
        conn.setInt(token, value);
    }
    
    /**
     * Subscribe to parameter updates
     * @param token 32-bit token
     * @param interval update interval in ms
     * @return subscription ID
     * @throws WApiException on error
     */
    public int subscribe(int token, int interval) throws WApiException {
        return conn.subscribe(token, interval);
    }
    
    /**
     * Register callback for updates
     */
    public void addCallback(WApiConnection.WApiCallback callback) {
        conn.addCallback(callback);
    }
    
    // ============================================================
    // Utility
    // ============================================================
    
    /**
     * Convert fader value (0.0-1.0) to dB display string
     */
    public static String faderToDbString(float fader) {
        if (fader <= 0f) {
            return "-inf dB";
        }
        // Fader range: 0.0 = -inf, 1.0 = +12dB
        float db = (float) (20 * Math.log10(fader) + 12);
        if (db < -60) {
            return "-inf dB";
        }
        return String.format("%.1f dB", db);
    }
    
    /**
     * Format fader percentage and dB
     */
    public static String faderToDisplay(float fader) {
        if (fader <= 0f) {
            return "0.0% (-inf dB)";
        }
        float db = (float) (20 * Math.log10(fader) + 12);
        float pct = fader * 100f;
        if (db < -60) {
            return String.format("%.1f%% (-inf dB)", pct);
        }
        return String.format("%.1f%% (%.1f dB)", pct, db);
    }
    
    /**
     * Format pan value to display string
     */
    public static String panToDisplay(float pan) {
        if (pan < -0.99f) return "L";
        if (pan > 0.99f) return "R";
        if (Math.abs(pan) < 0.01f) return "C";
        
        int lPct = Math.round((1f - pan) * 40f);
        int rPct = Math.round((1f + pan) * 40f);
        return "L" + lPct + "/R" + rPct;
    }
    
    /**
     * Get the underlying connection (for advanced use)
     */
    public WApiConnection getConnection() {
        return conn;
    }
}