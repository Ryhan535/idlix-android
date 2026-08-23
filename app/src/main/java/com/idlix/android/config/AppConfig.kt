package com.idlix.android.config

object AppConfig {
    /**
     * Base URL untuk API Backend IDLIX.
     * 
     * Opsi Penggunaan:
     * 1. VPS Server (Production / Remote Testing):
     *    - Format IP: "http://IP_VPS_ANDA:3000" (contoh: "http://103.150.90.12:3000")
     *    - Format Domain: "https://api.domainanda.com"
     * 
     * 2. Localhost Development (Development):
     *    - Android Emulator: "http://10.0.2.2:3000" (10.0.2.2 menunjuk ke localhost PC host)
     *    - HP Fisik (Satu Wi-Fi): "http://192.168.x.x:3000" (IP LAN PC Anda)
     * 
     * 3. Fallback Website Direct:
     *    - "https://z2.idlixku.com"
     */
    const val API_BASE_URL: String = "http://10.0.2.2:3000"

    /**
     * Domain Website IDLIX Utama (digunakan untuk fallback/scraping web langsung)
     */
    const val WEBSITE_BASE_URL: String = "https://z2.idlixku.com"

    /**
     * Normalisasi Base URL API agar tidak ada trailing slash '/' atau duplikasi path '/api'.
     */
    fun getNormalizedApiBaseUrl(): String {
        var url = API_BASE_URL.trim()
        while (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        if (url.endsWith("/api")) {
            url = url.substring(0, url.length - 4)
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        return url
    }
}
