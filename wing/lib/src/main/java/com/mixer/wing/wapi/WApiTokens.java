package com.mixer.wing.wapi;

/**
 * WING wapi Token Enum
 * 
 * Token naming convention: CH_1_FDR = ch.1.fader (channel 1 fader)
 * Based on wapi.h specification from pmaillot/wapi
 * 
 * Version: V1.0001
 */
public final class WApiTokens {

    // ============================================================
    // Channel Tokens (CH_1 to CH_48)
    // ============================================================
    
    // Mix (sends) - CH_xx_MIX_*
    public static final int CH_1_MIX_FADER = 0x01000001;
    public static final int CH_1_MIX_ON = 0x01000002;
    public static final int CH_2_MIX_FADER = 0x02000001;
    public static final int CH_2_MIX_ON = 0x02000002;
    public static final int CH_3_MIX_FADER = 0x03000001;
    public static final int CH_3_MIX_ON = 0x03000002;
    // ... (additional channels can be generated with helper method)
    
    // Preamp / Headamp - CH_xx_HA_*
    public static final int CH_1_HA_GAIN = 0x01000101;
    public static final int CH_1_HA_MUTE = 0x01000102;
    public static final int CH_2_HA_GAIN = 0x02000101;
    public static final int CH_2_HA_MUTE = 0x02000102;
    
    // EQ - CH_xx_EQ_*
    public static final int CH_1_EQ_ON = 0x01000201;
    public static final int CH_1_EQ_1_G = 0x01000211;  // EQ band 1 gain
    public static final int CH_1_EQ_1_F = 0x01000212;  // EQ band 1 freq
    public static final int CH_1_EQ_2_G = 0x01000221;
    public static final int CH_1_EQ_2_F = 0x01000222;
    public static final int CH_1_EQ_3_G = 0x01000231;
    public static final int CH_1_EQ_3_F = 0x01000232;
    public static final int CH_1_EQ_4_G = 0x01000241;
    public static final int CH_1_EQ_4_F = 0x01000242;
    
    // Gate - CH_xx_GATE_*
    public static final int CH_1_GATE_ON = 0x01000301;
    public static final int CH_1_GATE_LEVEL = 0x01000302;
    
    // Compressor - CH_xx_COMP_*
    public static final int CH_1_COMP_ON = 0x01000401;
    public static final int CH_1_COMP_LEVEL = 0x01000402;
    
    // Pan / Balance - CH_xx_*
    public static final int CH_1_PAN = 0x01000501;
    public static final int CH_1_PAN_LR = 0x01000502;  // Left-Right pan
    
    // ============================================================
    // Bus Tokens (BUS_1 to BUS_16)
    // ============================================================
    public static final int BUS_1_MIX_FADER = 0x10000001;
    public static final int BUS_1_MIX_ON = 0x10000002;
    public static final int BUS_1_PAN = 0x10000003;
    public static final int BUS_2_MIX_FADER = 0x11000001;
    public static final int BUS_2_MIX_ON = 0x11000002;
    
    // ============================================================
    // Main / Master Tokens (MAIN_L, MAIN_R, MAIN_M)
    // ============================================================
    public static final int MAIN_L_MIX_FADER = 0x20000001;
    public static final int MAIN_R_MIX_FADER = 0x20000002;
    public static final int MAIN_M_MIX_FADER = 0x20000003;
    public static final int MAIN_L_MIX_ON = 0x20000004;
    public static final int MAIN_R_MIX_ON = 0x20000005;
    public static final int MAIN_M_MIX_ON = 0x20000006;
    
    // ============================================================
    // Aux/FX Send Tokens
    // ============================================================
    // FX returns (Aux 1-8)
    public static final int AUX_1_MIX_FADER = 0x18000001;
    public static final int AUX_1_MIX_ON = 0x18000002;
    public static final int AUX_2_MIX_FADER = 0x18100001;
    public static final int AUX_2_MIX_ON = 0x18100002;
    
    // ============================================================
    // System / Config Tokens
    // ============================================================
    public static final int SYS_OS_VER = 0x30000001;  // OS Version (read-only)
    public static final int SYS_CPU_LOAD = 0x30000002; // CPU Load (read-only)
    
    // ============================================================
    // Token Helper Methods
    // ============================================================
    
    private WApiTokens() {} // Prevent instantiation
    
    /**
     * Generate channel mix fader token
     * @param ch channel number (1-48)
     * @return token value
     */
    public static int chMixFader(int ch) {
        return (ch & 0xFF) << 16 | 0x00000001;
    }
    
    /**
     * Generate channel mix ON/Mute token
     * @param ch channel number (1-48)
     * @return token value
     */
    public static int chMixOn(int ch) {
        return (ch & 0xFF) << 16 | 0x00000002;
    }
    
    /**
     * Generate channel pan token
     * @param ch channel number (1-48)
     * @return token value
     */
    public static int chPan(int ch) {
        return (ch & 0xFF) << 16 | 0x00000005;
    }
    
    /**
     * Generate channel EQ band gain token
     * @param ch channel number (1-48)
     * @param band EQ band (1-4)
     * @return token value
     */
    public static int chEqGain(int ch, int band) {
        return ((ch & 0xFF) << 16) | (0x02 << 8) | (band << 4) | 0x01;
    }
    
    /**
     * Generate channel preamp gain token
     * @param ch channel number (1-48)
     * @return token value
     */
    public static int chHaGain(int ch) {
        return ((ch & 0xFF) << 16) | 0x00000101;
    }
    
    /**
     * Generate bus mix fader token
     * @param bus bus number (1-16)
     * @return token value
     */
    public static int busMixFader(int bus) {
        return 0x10000000 | ((bus & 0xFF) << 16) | 0x00000001;
    }
    
    /**
     * Generate bus mix ON token
     * @param bus bus number (1-16)
     * @return token value
     */
    public static int busMixOn(int bus) {
        return 0x10000000 | ((bus & 0xFF) << 16) | 0x00000002;
    }
    
    /**
     * Token to string representation for debugging
     */
    public static String tokenToString(int token) {
        int hi = (token >> 16) & 0xFF;
        int mid = (token >> 8) & 0xFF;
        int lo = token & 0xFF;
        return String.format("0x%02X%02X%02X%02X", hi, mid, lo & 0xF0, lo & 0x0F);
    }
}