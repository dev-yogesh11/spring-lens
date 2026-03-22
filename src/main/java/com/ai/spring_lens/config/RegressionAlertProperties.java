package com.ai.spring_lens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "springlens.regression-alert")
public class RegressionAlertProperties {
    private boolean enabled = true;
    private double thresholdPercent = 15.0;
    private int lookbackDays = 7;
}