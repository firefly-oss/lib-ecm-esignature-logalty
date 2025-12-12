package com.firefly.ecm.adapter.logalty.services;

import com.firefly.ecm.adapter.logalty.SignatureProperties;
import com.firefly.ecm.adapter.logalty.dtos.BinaryContentsDTO;
import com.firefly.ecm.adapter.logalty.dtos.ReceiverDTO;
import com.firefly.ecm.adapter.logalty.exceptions.LogaltyCallException;
import com.logalty.constant.IncomingServiceResponseCode;
import com.logalty.constant.DataServiceResponseCode;
import com.logalty.constant.UpdateServiceResponse;
import com.logalty.exception.LogaltyException;
import com.logalty.interfaces.XmlSignInterface;
import com.logalty.schema.ptrequest.*;
import com.logalty.schema.ptrequest.BinarycontentsDocument.Binarycontents;
import com.logalty.schema.ptdatarequest.DataCertificateResponseDocument;
import com.logalty.schema.ptdatarequest.SignedBinaryResponseDocument;
import com.logalty.schema.ptdatarequest.DataStateExternalIdResponseDocument;
import com.logalty.schema.updaterequest.xmlbeans.CancelResponseDocument;
import com.logalty.sdk.webservice.Operation;
import com.logalty.sdk.xml.incoming.RequestDocumentBuilder;
import com.logalty.sdk.xml.update.cancel.CancelRequestDocumentBuilder;
import com.logalty.sdk.xml.data.DataCertificateRequestDocumentBuilder;
import com.logalty.sdk.xml.data.SignedBinaryRequestDocumentBuilder;
import com.logalty.sdk.xml.data.DataStateExternalIdRequestDocumentBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignatureService {

    private final LogaltyCommonService common;
    private final XmlSignInterface xmlSigner;

    /**
     * Initializes the signature process by validating the provided input and sending
     * the required information for processing. The method validates the list of receivers
     * and binary contents, constructs a request, performs the operation, and processes the response.
     *
     * @param receivers          the list of {@code ReceiverDTO} objects containing the information of the receivers involved
     * @param binaryContentsList the list of {@code BinaryContentsDTO} objects containing the binary content details
     * @return the result of the operation as a {@code ResultDocument.Result} object
     */
    public ResultDocument.Result initSignature(List<ReceiverDTO> receivers,
                                               List<BinaryContentsDTO> binaryContentsList) {

        common.validateInput(receivers, binaryContentsList);

        return common.executeOperation(
                () -> common.buildIncomingRequest(receivers, binaryContentsList),
                (xml, signer) -> common.post(Operation.INCOMING_OPERATION, xml, signer),
                common::parseIncomingResponse,
                response -> IncomingServiceResponseCode.getByCode(
                        response.getResponse().getResult().getMain()
                ) == IncomingServiceResponseCode.DOCUMENT_ACCEPTED,
                response -> response.getResponse().getResult().getReason(),
                "INIT_SIGNATURE"
        ).getResponse().getResult();
    }

    /**
     * Initializes the cancel operation by building and executing a signed cancel request.
     * The cancel operation is submitted and the response is parsed and validated.
     *
     * @param id The identifier of the resource to be canceled.
     * @return The cancel response document containing the result of the cancellation request.
     * @throws RuntimeException If there is an error building the signed request or executing the operation.
     */
    public CancelResponseDocument.CancelResponse initCancel(String id) {

        return common.executeOperation(
                () -> {
                    try {
                        return new CancelRequestDocumentBuilder(id, "Example of cancel reason")
                                .buildSigned(xmlSigner);
                    } catch (LogaltyException e) {
                        throw new RuntimeException("Error calling buildSigned", e);
                    }
                },
                (xml, signer) -> common.post(Operation.CANCEL_REQUEST_OPERATION, xml, signer),
                common::parseCancelResponse,
                response -> UpdateServiceResponse.getFromCode(response.getCancelResponse().getMain())
                        == UpdateServiceResponse.DOCUMENT_ACCEPTED,
                response -> "code=" + response.getCancelResponse().getMain(),
                id
        ).getCancelResponse();
    }

    /**
     * Retrieves a data certificate response for the given ID.
     *
     * @param id the identifier for which the certificate response is requested
     * @return the data certificate response associated with the provided ID
     * @throws RuntimeException if an error occurs during the process of building or posting the request
     */
    public DataCertificateResponseDocument.DataCertificateResponse getCertificate(String id) {

        return common.executeOperation(
                () -> {
                    try {
                        return new DataCertificateRequestDocumentBuilder(id)
                                .buildSigned(xmlSigner);
                    } catch (LogaltyException e) {
                        throw new RuntimeException("Error calling buildSigned", e);
                    }
                },
                (xml, signer) -> common.post(Operation.CERTIFICATE_REQUEST_OPERATION, xml, signer),
                common::parseCertificateResponse,
                response -> {
                    var r = response.getDataCertificateResponse();
                    return DataServiceResponseCode.getByCode(r.getMain()) == DataServiceResponseCode.DOCUMENT_ACCEPTED;
                },
                response -> "code=" + response.getDataCertificateResponse().getMain(),
                id
        ).getDataCertificateResponse();
    }

    /**
     * Retrieves the signed binary response for a given identifier.
     *
     * @param id the unique identifier for which the signed binary response is to be retrieved
     * @return the signed binary response associated with the provided identifier
     */
    public SignedBinaryResponseDocument.SignedBinaryResponse getSignedBinary(String id) {

        return common.executeOperation(
                () -> {
                    try {
                        return new SignedBinaryRequestDocumentBuilder(id)
                                .buildSigned(xmlSigner);
                    } catch (LogaltyException e) {
                        throw new RuntimeException("Error calling buildSigned", e);
                    }
                },
                (xml, signer) -> common.post(Operation.SIGNED_BINARY_OPERATION, xml, signer),
                common::parseSignedBinaryResponse,
                response -> {
                    var r = response.getSignedBinaryResponse();
                    return DataServiceResponseCode.getByCode(r.getMain()) == DataServiceResponseCode.DOCUMENT_ACCEPTED;
                },
                response -> "code=" + response.getSignedBinaryResponse().getMain(),
                id
        ).getSignedBinaryResponse();
    }

    /**
     * Retrieves the status of a document using an external identifier.
     *
     * @param externalId the external identifier of the document for which the status is being retrieved
     * @return the response object containing the status information of the document
     */
    public DataStateExternalIdResponseDocument.DataStateExternalIdResponse getStatus(String externalId) {

        return common.executeOperation(
                () -> {
                    try {
                        return new DataStateExternalIdRequestDocumentBuilder(externalId)
                                .buildSigned(xmlSigner);
                    } catch (LogaltyException e) {
                        throw new RuntimeException("Error calling buildSigned", e);
                    }
                },
                (xml, signer) -> common.post(Operation.STATES_EXTERNAL_ID_OPERATION, xml, signer),
                common::parseDataStateExternalIdResponse,
                response -> {
                    var r = response.getDataStateExternalIdResponse();
                    return DataServiceResponseCode.getByCode(r.getMain()) == DataServiceResponseCode.DOCUMENT_ACCEPTED;
                },
                response -> "code=" + response.getDataStateExternalIdResponse().getMain(),
                externalId
        ).getDataStateExternalIdResponse();
    }

}
