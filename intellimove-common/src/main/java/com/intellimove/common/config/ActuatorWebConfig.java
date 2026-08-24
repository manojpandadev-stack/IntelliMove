package com.intellimove.common.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Removes the actuator WelcomePageHandlerMapping bean from the application context.
 *
 * The WelcomePageHandlerMapping (org.springframework.boot.actuate.web.servlet)
 * has highest priority and intercepts /actuator/** requests before the
 * EndpointRequestHandlerMapping can handle them. This causes 406 errors
 * when Prometheus scrapes /actuator/prometheus with its openmetrics-text
 * Accept header.
 */
@Configuration
public class ActuatorWebConfig implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            String[] beanNames = registry.getBeanDefinitionNames();
            for (String name : beanNames) {
                if (name.contains("WelcomePageHandlerMapping") || name.contains("welcomePage")) {
                    registry.removeBeanDefinition(name);
                }
            }
        }
    }
}
