package com.firefly.ecm.adapter.logalty.services;

import com.firefly.ecm.adapter.logalty.SignatureProperties;
import com.firefly.ecm.adapter.logalty.dtos.BinaryContentsDTO;
import com.firefly.ecm.adapter.logalty.dtos.ReceiverDTO;
import com.firefly.ecm.adapter.logalty.exceptions.LogaltyCallException;
import com.logalty.interfaces.XmlSignInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Security;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LogaltyCommonServiceTest {

    private SignatureProperties props;
    private XmlSignInterface signer;
    private LogaltyCommonService service;

    @BeforeEach
    void init() {
        props = new SignatureProperties();
        signer = mock(XmlSignInterface.class);
        service = new LogaltyCommonService(props, signer);
    }

    @AfterEach
    void cleanup() {
        // Allow other tests to set their own trust store if needed
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStoreType");
    }

    @Test
    void executeOperation_success_returnsParsedValue() {
        // Ensure truststore path is clear to exercise initialization
        System.clearProperty("javax.net.ssl.trustStore");

        Supplier<String> xmlBuilder = () -> "<xml/>";
        BiFunction<String, XmlSignInterface, String> post = (xml, s) -> "response-body";
        Function<String, String> parser = resp -> "parsed";
        Function<String, Boolean> validator = resp -> true;
        Function<String, String> errorExtractor = resp -> "";

        String result = service.executeOperation(xmlBuilder, post, parser, validator, errorExtractor, "TEST");
        assertEquals("parsed", result);
        assertNotNull(System.getProperty("javax.net.ssl.trustStore"));
    }

    @Test
    void executeOperation_failure_throwsLogaltyCallException() {
        Supplier<String> xmlBuilder = () -> "<xml/>";
        BiFunction<String, XmlSignInterface, String> post = (xml, s) -> "response-body";
        Function<String, String> parser = resp -> "parsed";
        Function<String, Boolean> validator = resp -> false;
        Function<String, String> errorExtractor = resp -> "bad";

        LogaltyCallException ex = assertThrows(LogaltyCallException.class, () ->
            service.executeOperation(xmlBuilder, post, parser, validator, errorExtractor, "FAIL")
        );
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void validateInput_throwsOnNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.validateInput(null, List.of(new BinaryContentsDTO())));
        assertThrows(IllegalArgumentException.class, () -> service.validateInput(List.of(new ReceiverDTO()), null));
        assertThrows(IllegalArgumentException.class, () -> service.validateInput(List.of(), List.of(new BinaryContentsDTO())));
        assertThrows(IllegalArgumentException.class, () -> service.validateInput(List.of(new ReceiverDTO()), List.of()));
    }

    @Test
    void setupSecurityProviders_addsBouncyCastle() {
        try { Security.removeProvider("BC"); } catch (Exception ignored) {}
        assertNull(Security.getProvider("BC"));
        service.setupSecurityProviders();
        assertNotNull(Security.getProvider("BC"));
    }

    @Test
    void initTrustStoreFromClasspath_setsSystemProperties() {
        System.clearProperty("javax.net.ssl.trustStore");
        service.initTrustStoreFromClasspath();
        assertNotNull(System.getProperty("javax.net.ssl.trustStore"));
        assertEquals("MiPassSeguro123", System.getProperty("javax.net.ssl.trustStorePassword"));
        assertEquals("JKS", System.getProperty("javax.net.ssl.trustStoreType"));
    }

    @Test
    void parseMethods_throwOnInvalidXml() {
        String invalid = "not-xml";
        assertThrows(LogaltyCallException.class, () -> service.parseIncomingResponse(invalid));
        assertThrows(LogaltyCallException.class, () -> service.parseCancelResponse(invalid));
        assertThrows(LogaltyCallException.class, () -> service.parseCertificateResponse(invalid));
        assertThrows(LogaltyCallException.class, () -> service.parseSignedBinaryResponse(invalid));
        assertThrows(LogaltyCallException.class, () -> service.parseDataStateExternalIdResponse(invalid));
    }
}
