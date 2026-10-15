package com.lucky.common.net;

/**
 * 本机 Feign / HTTP 走 SOCKS 或 HTTP 代理时，避免 127.0.0.1、localhost 被代理导致连不上本地服务。
 * 在各微服务 {@code main} 里、{@code SpringApplication.run} 之前调用 {@link #enable()}。
 */
public final class LoopbackProxyBypass {

    private static final String LOOPBACK_HOSTS = "localhost|127.*|[::1]|0:0:0:0:0:0:0:1";

    private LoopbackProxyBypass() {
    }

    public static void enable() {
        appendNonProxyHosts("socksNonProxyHosts", LOOPBACK_HOSTS);
        appendNonProxyHosts("http.nonProxyHosts", LOOPBACK_HOSTS);
    }

    private static void appendNonProxyHosts(String propertyName, String loopbackHosts) {
        String configuredHosts = System.getProperty(propertyName, "");
        if (configuredHosts.isBlank()) {
            System.setProperty(propertyName, loopbackHosts);
            return;
        }
        if (!configuredHosts.contains("127.*")) {
            System.setProperty(propertyName, configuredHosts + "|" + loopbackHosts);
        }
    }
}
