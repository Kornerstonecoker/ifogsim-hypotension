package org.fog.test.perfeval;

public class Constants {
    // Device Configurations (inspired by lab_09 Table 2)
    public static final int SENSOR_MIPS = 100;
    public static final int ACTUATOR_MIPS = 50;
    public static final int BIOMETRIC_MIPS = 200;
    public static final int FOG_MIPS = 3000; // Similar to WiFi gateway
    public static final int FOG_RAM = 4096; // 4GB
    public static final double FOG_POWER_MAX = 107.3399;
    public static final double FOG_POWER_IDLE = 83.433;
    public static final int CLOUD_MIPS = 3000; // Cloud VM
    public static final int CLOUD_RAM = 4096;
    public static final double CLOUD_POWER_MAX = 107.3399;
    public static final double CLOUD_POWER_IDLE = 83.433;

    // Network Configurations (inspired by lab_09 Table 6)
    public static final int LORAWAN_LATENCY = 15; // ms, similar to Smartphone-WiFi gateway
    public static final int WAN_LATENCY = 125;    // ms, adjusted for cloud
    public static final int LORAWAN_BANDWIDTH = 50; // kbps
    public static final int WAN_BANDWIDTH = 10000;  // kbps
}
