package com.example.wifibasedattendanceapplication;

public class WifiConfig {
    
    // University WiFi Network Names (SSIDs)
    // Add all possible WiFi network names your university uses
    public static final String[] UNIVERSITY_WIFI_SSIDS = {
        "vivo1920",
        "PU-Student_WiFi"
        // Add more SSIDs as needed
    };
    
    // University WiFi BSSIDs (as fallback)
    // These are specific access point identifiers
    public static final String[] UNIVERSITY_WIFI_BSSIDS = {
        "26:ba:0d:90:d2:e4",
        "48:2f:6b:7a:a2:32"
    };
    
    // Keywords to identify university WiFi networks
    // The app will check if the SSID contains any of these keywords
    public static final String[] UNIVERSITY_WIFI_KEYWORDS = {
        "university", 
        "campus",
        "college",
        "institute"
        // Add more keywords as needed
    };
    
    // Configuration options
    public static final boolean TRUST_MODE_ENABLED = true; // Set to true to trust WiFi connections when SSID is unknown
    public static final boolean ALLOW_UNKNOWN_SSID = true; // Set to true to allow access when SSID is "<unknown ssid>"
    

    public static boolean shouldTrustCurrentConnection() {
        return TRUST_MODE_ENABLED;
    }
    
    /**
     * Check if we should allow access when SSID is unknown
     */
    public static boolean shouldAllowUnknownSSID() {
        return ALLOW_UNKNOWN_SSID;
    }
    
    /**
     * Check if a given SSID matches any university WiFi network
     */
    public static boolean isUniversityWiFiSSID(String ssid) {
        android.util.Log.d("WifiConfig", "Checking SSID: " + ssid);
        
        if (ssid == null || ssid.isEmpty()) {
            android.util.Log.d("WifiConfig", "SSID is null or empty");
            return false;
        }
        
        // Remove quotes if present
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
            android.util.Log.d("WifiConfig", "Removed quotes, SSID now: " + ssid);
        }
        
        String ssidLower = ssid.toLowerCase();
        android.util.Log.d("WifiConfig", "SSID lowercase: " + ssidLower);
        
        // Check exact matches
        for (String universitySSID : UNIVERSITY_WIFI_SSIDS) {
            android.util.Log.d("WifiConfig", "Checking against university SSID: " + universitySSID);
            if (ssid.equals(universitySSID)) {
                android.util.Log.d("WifiConfig", "Exact SSID match found: " + universitySSID);
                return true;
            }
        }
        
        // Check keyword matches
        for (String keyword : UNIVERSITY_WIFI_KEYWORDS) {
            android.util.Log.d("WifiConfig", "Checking keyword: " + keyword);
            if (ssidLower.contains(keyword.toLowerCase())) {
                android.util.Log.d("WifiConfig", "Keyword match found: " + keyword);
                return true;
            }
        }
        
        android.util.Log.d("WifiConfig", "No SSID match found");
        return false;
    }
    
    /**
     * Check if a given BSSID matches any university WiFi access point
     */
    public static boolean isUniversityWiFiBSSID(String bssid) {
        android.util.Log.d("WifiConfig", "Checking BSSID: " + bssid);
        
        if (bssid == null || bssid.isEmpty()) {
            android.util.Log.d("WifiConfig", "BSSID is null or empty");
            return false;
        }
        
        for (String universityBSSID : UNIVERSITY_WIFI_BSSIDS) {
            android.util.Log.d("WifiConfig", "Checking against university BSSID: " + universityBSSID);
            if (universityBSSID.equals(bssid)) {
                android.util.Log.d("WifiConfig", "BSSID match found: " + universityBSSID);
                return true;
            }
        }

        
        android.util.Log.d("WifiConfig", "No BSSID match found");
        return false;
    }
}
