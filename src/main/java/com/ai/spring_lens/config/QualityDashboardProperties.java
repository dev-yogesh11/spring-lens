package com.ai.spring_lens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "springlens.quality-dashboard")
public class QualityDashboardProperties {
    private int defaultDays = 7;
    private int maxDays = 90;
}