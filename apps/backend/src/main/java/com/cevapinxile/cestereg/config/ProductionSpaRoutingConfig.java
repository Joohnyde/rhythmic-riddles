package com.cevapinxile.cestereg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Production-only SPA routing:
 * - Serves /index.html for any path that does NOT contain a dot (.) i.e. not a static file.
 * - Does NOT interfere with /api/**, /ws/** etc because those have real handler mappings and win first.
 * - Cannot loop, because index.html contains a dot and will not match the regex mappings below.
 */
@Configuration
@Profile("production")
public class ProductionSpaRoutingConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Root -> index.html
        registry.addViewController("/").setViewName("forward:/index.html");

        // Any single-segment path without a dot, e.g. /tvapp
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");

        // Any multi-segment path without a dot, e.g. /admin/settings
        registry.addViewController("/**/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}