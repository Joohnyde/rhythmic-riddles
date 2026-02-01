package com.cevapinxile.cestereg.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.assets")
public class AssetProperties {
    private String baseDir;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }
}
