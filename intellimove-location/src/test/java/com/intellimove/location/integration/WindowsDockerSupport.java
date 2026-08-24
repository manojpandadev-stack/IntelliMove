package com.intellimove.location.integration;

import org.testcontainers.DockerClientFactory;

/**
 * Configures Testcontainers for Docker Desktop on Windows (named pipe) and
 * reports why containers cannot start.
 */
final class WindowsDockerSupport {

    private static final String NPIPE = "npipe:////./pipe/docker_engine";

    private WindowsDockerSupport() {}

    static void install() {
        if (!isWindows()) {
            return;
        }
        if (isBlank(System.getenv("DOCKER_HOST")) && isBlank(System.getProperty("docker.host"))) {
            System.setProperty("docker.host", NPIPE);
        }
        System.setProperty("testcontainers.ryuk.disabled", "true");
        System.setProperty("docker.client.strategy",
                "org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy");
    }

    static String blockedReason() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return null;
            }
        } catch (Throwable t) {
            return dockerUnavailableMessage() + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
        }
        return dockerUnavailableMessage();
    }

    private static String dockerUnavailableMessage() {
        if (isWindows()) {
            return "BLOCKED: Docker/Testcontainers cannot access the Windows Docker named pipe "
                    + "(expected " + NPIPE + "). Start Docker Desktop, ensure the pipe is exposed, "
                    + "and grant this process permission to use docker.engine.";
        }
        return "BLOCKED: Docker is not available for Testcontainers on this host.";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
