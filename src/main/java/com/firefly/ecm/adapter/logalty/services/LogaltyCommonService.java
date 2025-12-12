package com.firefly.ecm.adapter.logalty.services;

import com.firefly.ecm.adapter.logalty.SignatureProperties;
import com.firefly.ecm.adapter.logalty.dtos.BinaryContentsDTO;
import com.firefly.ecm.adapter.logalty.dtos.ReceiverDTO;
import com.firefly.ecm.adapter.logalty.exceptions.LogaltyCallException;
import com.logalty.constant.NodeProcess;
import com.logalty.exception.LogaltyException;
import com.logalty.schema.ptrequest.*;
import com.logalty.schema.ptrequest.BinarycontentgroupDocument.Binarycontentgroup;
import com.logalty.schema.ptrequest.BinarycontentgroupsDocument.Binarycontentgroups;
import com.logalty.schema.ptrequest.BinarycontentitemsDocument.Binarycontentitems;
import com.logalty.schema.ptrequest.BinarycontentruleDocument.Binarycontentrule;
import com.logalty.schema.ptrequest.BinarycontentsDocument.Binarycontents;
import com.logalty.schema.ptrequest.ContactDocument.Contact;
import com.logalty.schema.ptrequest.LegalIdentityDocument.LegalIdentity;
import com.logalty.schema.ptrequest.MetaUserdefinedDocument1.MetaUserdefinedDocument;
import com.logalty.schema.ptrequest.PersonalDataDocument.PersonalData;
import com.logalty.schema.ptrequest.Receiver.Binarycontentrules;
import com.logalty.schema.ptdatarequest.DataCertificateResponseDocument;
import com.logalty.schema.ptdatarequest.DataStateExternalIdResponseDocument;
import com.logalty.schema.ptdatarequest.SignedBinaryResponseDocument;
import com.logalty.schema.updaterequest.xmlbeans.CancelResponseDocument;
import com.logalty.interfaces.XmlSignInterface;
import com.logalty.sdk.webservice.HttpSender;
import com.logalty.sdk.webservice.Operation;
import com.logalty.sdk.webservice.Proxy;
import com.logalty.sdk.webservice.ServerInfo;
import com.logalty.sdk.xml.incoming.ContactBuilder;
import com.logalty.sdk.xml.incoming.LegalIdentityBuilder;
import com.logalty.sdk.xml.incoming.PersonalDataBuilder;
import com.logalty.sdk.xml.incoming.RequestDocumentBuilder;
import com.logalty.sdk.xml.incoming.binarycontent.*;
import com.logalty.sdk.xml.incoming.person.ReceiverBuilder;
import com.logalty.sdk.xml.incoming.person.ReceiversBuilder;
import com.logalty.sdk.xml.incoming.processmeta.ProcessMetaBuilder;
import com.logalty.sdk.xml.incoming.processmeta.UserdefinedBuilder;
import com.logalty.sdk.xml.incoming.requestmeta.MetaUserdefinedDocumentBuilder;
import com.logalty.sdk.xml.incoming.requestmeta.RequestMetaBuilder;
import com.logalty.sdk.xml.incoming.requestmeta.Time2CloseBuilder;
import com.logalty.sdk.xml.incoming.requestmeta.Time2SaveBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.XmlException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogaltyCommonService {

    private final SignatureProperties signatureProperties;
    private final XmlSignInterface xmlSigner;

    public <T> T executeOperation(
            Supplier<String> xmlBuilder,
            BiFunction<String, XmlSignInterface, String> postFunction,
            Function<String, T> responseParser,
            Function<T, Boolean> successValidator,
            Function<T, String> errorMessageExtractor,
            String id
    ) {
        log.info("Operation [{}]: start", id);
        initTrustStoreFromClasspath();

        // Build XML
        log.debug("Operation [{}]: building XML request...", id);
        String xmlRequest = xmlBuilder.get();
        log.debug("Operation [{}]: XML built (length={})", id, xmlRequest != null ? xmlRequest.length() : 0);

        // POST
        log.info("Operation [{}]: sending request to Logalty...", id);
        String responseString = postFunction.apply(xmlRequest, xmlSigner);
        log.debug("Operation [{}]: response received (length={})", id, responseString != null ? responseString.length() : 0);

        // Parse
        log.debug("Operation [{}]: parsing response...", id);
        T response = responseParser.apply(responseString);

        // Validate
        log.debug("Operation [{}]: validating response...", id);
        if (!successValidator.apply(response)) {
            String message = errorMessageExtractor.apply(response);
            log.warn("Operation [{}] failed validation: {}", id, message);
            throw new LogaltyCallException(
                    "Operation error (" + id + "): " + message,
                    id,
                    message,
                    null
            );
        }

        log.info("Operation [{}]: success", id);
        return response;
    }

    public String buildIncomingRequest(List<ReceiverDTO> receivers,
                                        List<BinaryContentsDTO> binaryContentsList) {

        RequestMetaDocument.RequestMeta requestMeta =
                generateRequestMeta(
                        signatureProperties.getRetryProtocol(),
                        signatureProperties.isSynchronous()
                );

        ProcessMetaDocument.ProcessMeta processMeta = generateProcessMeta(receivers);
        Binarycontents binarycontents = createBinaryContents(binaryContentsList);

        try {
            return new RequestDocumentBuilder(requestMeta, processMeta, binarycontents)
                    .buildSigned(xmlSigner);
        } catch (LogaltyException e) {
            log.error("Error building and signing request document", e);
            LogaltyCallException ex = new LogaltyCallException("Error calling buildSigned");
            ex.initCause(e);
            throw ex;
        }
    }

    public void validateInput(List<ReceiverDTO> receivers, List<BinaryContentsDTO> binaryContentsList) {
        if (receivers == null || receivers.isEmpty()) {
            throw new IllegalArgumentException("Receiver list cannot be null or empty");
        }
        if (binaryContentsList == null || binaryContentsList.isEmpty()) {
            throw new IllegalArgumentException("Binary contents list cannot be null or empty");
        }
    }

    public void setupSecurityProviders() {
        if (Security.getProvider("BC") == null) {
            log.debug("Adding BouncyCastle provider...");
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public void initTrustStoreFromClasspath() {

        if (System.getProperty("javax.net.ssl.trustStore") != null) {
            return;
        }

        String resource = signatureProperties.getTrustStoreResource();
        String password = signatureProperties.getTrustStorePassword();
        String type = signatureProperties.getTrustStoreType() != null
                ? signatureProperties.getTrustStoreType()
                : "JKS";

        log.debug("Loading truststore (resource='{}', type='{}')...", resource, type);

        try (InputStream in = resolveResourceAsStream(resource)) {

            if (in == null) {
                throw new IllegalStateException("TrustStore resource not found: " + resource);
            }

            Path temp = Files.createTempFile("truststore", "." + type.toLowerCase());
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);

            System.setProperty("javax.net.ssl.trustStore", temp.toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", password);
            System.setProperty("javax.net.ssl.trustStoreType", type);

            log.info("Truststore initialized at temporary location: {}", temp);

        } catch (Exception e) {
            log.error("Error initializing truststore from '{}'", resource, e);
            throw new IllegalStateException("Error initializing truststore", e);
        }
    }

    private InputStream resolveResourceAsStream(String resource) throws IOException {

        if (resource == null || resource.isBlank()) {
            return null;
        }

        String res = resource.trim();

        // Explicit classpath:
        if (res.startsWith("classpath:")) {
            String cp = res.substring("classpath:".length());
            if (!cp.startsWith("/")) cp = "/" + cp;
            return getClass().getResourceAsStream(cp);
        }

        // As a file path:
        Path path = Paths.get(res);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }

        // Fallback to classpath lookup:
        String cp = res.startsWith("/") ? res : "/" + res;
        return getClass().getResourceAsStream(cp);
    }



    public String post(Operation operation, String xmlRequest, XmlSignInterface signer) {
        ServerInfo serverInfo = ServerInfo.LOGALTY_DEMO_SERVER;
        Proxy proxy = Proxy.DISABLED_PROXY;

        setupSecurityProviders();
        log.info("Sending {} to Logalty...", operation.name());
        try {
            return HttpSender.postRequest(
                operation,
                xmlRequest,
                signer.retrieveCertificateId(),
                serverInfo,
                proxy
            );
        } catch (LogaltyException e) {
            log.error("Error calling post for operation {}", operation.name(), e);
            LogaltyCallException ex = new LogaltyCallException("Error calling post");
            ex.initCause(e);
            throw ex;
        }
    }

    public ResponseDocument parseIncomingResponse(String responseString) {
        try {
            return ResponseDocument.Factory.parse(responseString);
        } catch (Exception e) {
            log.error("Invalid Incoming response from Logalty: {}", responseString);
            LogaltyCallException ex = new LogaltyCallException("Error calling parseIncomingResponse");
            ex.initCause(e);
            throw ex;
        }
    }

    public CancelResponseDocument parseCancelResponse(String responseString) {
        try {
            return CancelResponseDocument.Factory.parse(responseString);
        } catch (XmlException e) {
            log.error("Error parsing CancelResponse", e);
            LogaltyCallException ex = new LogaltyCallException("Error calling parseCancelResponse");
            ex.initCause(e);
            throw ex;
        }
    }

    public DataCertificateResponseDocument parseCertificateResponse(String responseString) {
        try {
            return DataCertificateResponseDocument.Factory.parse(responseString);
        } catch (XmlException e) {
            log.error("Error parsing DataCertificateResponse", e);
            LogaltyCallException ex = new LogaltyCallException("Error calling parseCertificateResponse");
            ex.initCause(e);
            throw ex;
        }
    }

    public SignedBinaryResponseDocument parseSignedBinaryResponse(String responseString) {
        try {
            return SignedBinaryResponseDocument.Factory.parse(responseString);
        } catch (XmlException e) {
            log.error("Error parsing SignedBinaryResponse", e);
            LogaltyCallException ex = new LogaltyCallException("Error calling parseSignedBinaryResponse");
            ex.initCause(e);
            throw ex;
        }
    }

    public DataStateExternalIdResponseDocument parseDataStateExternalIdResponse(String responseString) {
        try {
            return DataStateExternalIdResponseDocument.Factory.parse(responseString);
        } catch (XmlException e) {
            log.error("Error parsing DataStateExternalIdResponse", e);
            LogaltyCallException ex = new LogaltyCallException("Error calling parseDataStateExternalIdResponse");
            ex.initCause(e);
            throw ex;
        }
    }

    public ProcessMetaDocument.ProcessMeta generateProcessMeta(List<ReceiverDTO> receiverDTOS) {
        ReceiversDocument.Receivers receivers = generateReceivers(receiverDTOS);

        UserdefinedBuilder userDefinedBuilder = new UserdefinedBuilder("name", "value");
        UserdefinedDocument.Userdefined[] userDefinedArray = {userDefinedBuilder.build()};

        return new ProcessMetaBuilder(
            signatureProperties.getGeneratorName(),
            com.logalty.constant.NodeLanguage.ES,
            receivers,
            "subject",
            "body",
            "LOGALTY_DIRECT_ACCESS_DOC_IN_FRAME",
            signatureProperties.getGeneratorEmail(),
            userDefinedArray
        ).build();
    }

    public ReceiversDocument.Receivers generateReceivers(List<ReceiverDTO> receivers) {
        log.info("Generating Receivers: count={}", receivers != null ? receivers.size() : 0);
        List<Receiver> receiverList = receivers.stream()
            .map(this::generateReceiver)
            .toList();

        ReceiversDocument.Receivers built = new ReceiversBuilder(receiverList).build();
        log.debug("Receivers generated successfully (count={})", receiverList.size());
        return built;
    }

    public Receiver generateReceiver(ReceiverDTO dto) {
        log.debug(
                "Generating Receiver: receiverId={}, ruleId={}, groupId={}, docType={}, country={}",
                dto.getReceiverId(), dto.getRuleId(), dto.getGroupId(), dto.getDocumentType(), dto.getDocumentCountry()
        );
        PersonalData personalData = new PersonalDataBuilder(
            dto.getName(),
            "",
            dto.getLast1(),
            dto.getLast2()
        ).build();

        Contact contact = new ContactBuilder(
            dto.getUuid(),
            "",
            dto.getTelephone(),
            ""
        )
            .email(dto.getEmail())
            .preferred("EMAIL")
            .noticeMethod("EMAIL")
            .build();

        LegalIdentity legalIdentity = new LegalIdentityBuilder(
            dto.getDocumentType(),
            dto.getDocumentCountry(),
            "",
            dto.getDocumentNumber(),
            ""
        ).build();

        Binarycontentrule rule =
            new BinarycontentruleBuilder(dto.getRuleId(), dto.getGroupId()).build();

        Binarycontentrules binarycontentrules =
            new BinarycontentrulesBuilder(List.of(rule)).build();

        Receiver receiver = new ReceiverBuilder(
            personalData,
            contact,
            legalIdentity,
            dto.getReceiverId(),
            binarycontentrules
        ).build();
        log.debug("Receiver generated: receiverId={}", dto.getReceiverId());
        return receiver;
    }

    public RequestMetaDocument.RequestMeta generateRequestMeta(int retryProtocol, boolean synchronous) {
        MetaUserdefinedDocumentBuilder metaUserDefinedDocumentBuilder =
            new MetaUserdefinedDocumentBuilder("java", "open jdk version 20");

        Time2CloseDocument.Time2Close time2Close = new Time2CloseBuilder(10, com.logalty.constant.LogaltyTimeUnit.DAY).build();
        Time2SaveDocument.Time2Save time2Save = new Time2SaveBuilder(1825, com.logalty.constant.LogaltyTimeUnit.DAY).build();

        return new RequestMetaBuilder(
            NodeProcess.ACCEPTANCEXPRESS,
            time2Close,
            time2Save,
            0,
            retryProtocol,
            synchronous,
            0,
            new MetaUserdefinedDocument[]{metaUserDefinedDocumentBuilder.build()}
        ).build();
    }

    public Binarycontents createBinaryContents(List<BinaryContentsDTO> binaryContentsDTOS) {
        log.info("Creating BinaryContents: items={}", binaryContentsDTOS != null ? binaryContentsDTOS.size() : 0);
        List<Integer> memberIds = Objects.requireNonNull(binaryContentsDTOS).stream()
            .map(BinaryContentsDTO::getBinaryContentId)
            .collect(Collectors.toList());

        List<Binarycontent> items = binaryContentsDTOS.stream()
            .map(dto -> {
                byte[] contents = dto.getContents();
                if (contents == null || contents.length == 0) {
                    throw new LogaltyCallException("Binary contents cannot be null or empty for id=" + dto.getBinaryContentId());
                }

                Binarycontent binaryContent = new BinarycontentBuilder(
                    dto.getBinaryContentId(),
                    dto.getEncoding(),
                    dto.getFilename() + "." + dto.getExtension(),
                    dto.getType(),
                    Base64.getEncoder().encodeToString(contents)
                ).build();

                log.debug("Added binary content item: id={}, filename={}.{}, sizeBytes={}",
                    dto.getBinaryContentId(), dto.getFilename(), dto.getExtension(), contents.length);
                return binaryContent;
            })
            .collect(Collectors.toList());

        Binarycontentgroup group = new BinarycontentgroupBuilder(1, memberIds).build();
        Binarycontentgroups binarycontentgroups = new BinarycontentgroupsBuilder(group).build();
        Binarycontentitems binarycontentitems = new BinarycontentitemsBuilder(items).build();

        Binarycontents result = new BinarycontentsBuilder(binarycontentgroups, binarycontentitems).build();
        log.info("BinaryContents created successfully with {} item(s)", items.size());
        return result;
    }
}
