package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings

/**
 * Manages proxy server settings
 */
class ProxySettingsManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    companion object {
        private const val MIN_PORT = 1024
        private const val MAX_PORT = 65535
    }

    fun isProxyAutoStartEnabled(): Boolean {
        return settings.proxyAutoStart
    }

    fun setProxyAutoStart(enabled: Boolean) {
        settings.proxyAutoStart = enabled
        onStateChanged()
    }

    fun getProxyPort(): Int {
        return settings.proxyPort
    }

    fun setProxyPort(port: Int) {
        require(port == 0 || port in MIN_PORT..MAX_PORT) { "Port must be 0 (auto) or between $MIN_PORT-$MAX_PORT" }
        settings.proxyPort = port
        onStateChanged()
    }

    fun getProxyPortRangeStart(): Int {
        return settings.proxyPortRangeStart
    }

    fun setProxyPortRangeStart(port: Int) {
        require(port in MIN_PORT..MAX_PORT) { "Port must be between $MIN_PORT-$MAX_PORT" }
        require(port <= settings.proxyPortRangeEnd) { "Start port must be <= end port" }
        settings.proxyPortRangeStart = port
        onStateChanged()
    }

    fun getProxyPortRangeEnd(): Int {
        return settings.proxyPortRangeEnd
    }

    fun setProxyPortRangeEnd(port: Int) {
        require(port in MIN_PORT..MAX_PORT) { "Port must be between $MIN_PORT-$MAX_PORT" }
        require(port >= settings.proxyPortRangeStart) { "End port must be >= start port" }
        settings.proxyPortRangeEnd = port
        onStateChanged()
    }

    fun setProxyPortRange(startPort: Int, endPort: Int) {
        require(startPort in MIN_PORT..MAX_PORT) { "Start port must be between $MIN_PORT-$MAX_PORT" }
        require(endPort in MIN_PORT..MAX_PORT) { "End port must be between $MIN_PORT-$MAX_PORT" }
        require(startPort <= endPort) { "Start port must be <= end port" }
        settings.proxyPortRangeStart = startPort
        settings.proxyPortRangeEnd = endPort
        onStateChanged()
    }
}
