package com.intellimove.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures Spring MVC to not intercept actuator paths via
 * the WelcomePageHandlerMapping. Explicit resource mapping
 * ensures static resources don't conflict with actuator endpoints.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Prevent the auto-configured WelcomePageHandlerMapping from
        // intercepting requests to /actuator/** paths.
        // By explicitly defining resource handlers, we take control
        // of the resource mapping and prevent fallback mappings.
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // No view controllers needed for backend services
    }
}
