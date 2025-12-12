package com.firefly.ecm.adapter.logalty;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "firefly.ecm.adapter.logalty.signature")
public class SignatureProperties {
    private String generatorName = "SOON";
    private String generatorEmail = "info@soon.es";
    private int retryProtocol = 3;
    private boolean synchronous = false;
    private String certPath = "src/main/resources/7694_SIGNATURE.pfx";
    private String certPin = "logalty";

    // TrustStore configuration (moved from hardcoded values)
    // Defaults keep backward compatibility with previous implementation
    private String trustStoreResource = "/mi-truststore.jks";
    private String trustStorePassword = "MiPassSeguro123";
    private String trustStoreType = "JKS";
}
