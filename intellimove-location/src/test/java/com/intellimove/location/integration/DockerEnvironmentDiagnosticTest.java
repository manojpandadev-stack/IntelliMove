package com.intellimove.location.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Always-on probe so Docker/Testcontainers failures are reported as BLOCKED
 * with the environment reason instead of silent skips or false passes.
 */
class DockerEnvironmentDiagnosticTest {

    static {
        WindowsDockerSupport.install();
    }

    @Test
    @DisplayName("Docker environment for Testcontainers")
    void dockerEnvironment() {
        String blocked = WindowsDockerSupport.blockedReason();
        Assumptions.assumeTrue(blocked == null, blocked);
    }
}
