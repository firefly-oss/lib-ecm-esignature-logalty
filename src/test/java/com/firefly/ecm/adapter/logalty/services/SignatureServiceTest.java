package com.firefly.ecm.adapter.logalty.services;

import com.firefly.ecm.adapter.logalty.dtos.BinaryContentsDTO;
import com.firefly.ecm.adapter.logalty.dtos.ReceiverDTO;
import com.logalty.interfaces.XmlSignInterface;
import com.logalty.schema.ptdatarequest.DataCertificateResponseDocument;
import com.logalty.schema.ptdatarequest.DataStateExternalIdResponseDocument;
import com.logalty.schema.ptdatarequest.SignedBinaryResponseDocument;
import com.logalty.schema.ptrequest.ResponseDocument;
import com.logalty.schema.ptrequest.ResultDocument;
import com.logalty.schema.updaterequest.xmlbeans.CancelResponseDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SignatureServiceTest {

    private LogaltyCommonService common;
    private XmlSignInterface xmlSigner;
    private SignatureService service;

    @BeforeEach
    void setUp() {
        common = mock(LogaltyCommonService.class);
        xmlSigner = mock(XmlSignInterface.class);
        service = new SignatureService(common, xmlSigner);
    }

    @Test
    void initSignature_delegatesToCommonAndReturnsResult() {
        // Given
        List<ReceiverDTO> receivers = List.of(new ReceiverDTO());
        List<BinaryContentsDTO> binaries = List.of(new BinaryContentsDTO());

        ResponseDocument responseDocument = mock(ResponseDocument.class);
        ResponseDocument.Response response = mock(ResponseDocument.Response.class);
        ResultDocument.Result result = mock(ResultDocument.Result.class);

        when(responseDocument.getResponse()).thenReturn(response);
        when(response.getResult()).thenReturn(result);

        when(common.executeOperation(
                any(), any(), any(), any(), any(), eq("INIT_SIGNATURE"))
        ).thenReturn(responseDocument);

        // When
        var out = service.initSignature(receivers, binaries);

        // Then
        verify(common).validateInput(receivers, binaries);
        assertSame(result, out);
    }

    @Test
    void initCancel_delegatesAndReturnsInner() {
        String id = "ABC-123";

        CancelResponseDocument cancelResponseDocument = mock(CancelResponseDocument.class);
        CancelResponseDocument.CancelResponse cancelResponse = mock(CancelResponseDocument.CancelResponse.class);
        when(cancelResponseDocument.getCancelResponse()).thenReturn(cancelResponse);

        when(common.executeOperation(any(), any(), any(), any(), any(), eq(id)))
                .thenReturn(cancelResponseDocument);

        var out = service.initCancel(id);
        assertSame(cancelResponse, out);
    }

    @Test
    void getCertificate_delegatesAndReturnsInner() {
        String id = "CERT-1";

        DataCertificateResponseDocument doc = mock(DataCertificateResponseDocument.class);
        DataCertificateResponseDocument.DataCertificateResponse inner =
                mock(DataCertificateResponseDocument.DataCertificateResponse.class);
        when(doc.getDataCertificateResponse()).thenReturn(inner);

        when(common.executeOperation(any(), any(), any(), any(), any(), eq(id)))
                .thenReturn(doc);

        var out = service.getCertificate(id);
        assertSame(inner, out);
    }

    @Test
    void getSignedBinary_delegatesAndReturnsInner() {
        String id = "BIN-1";

        SignedBinaryResponseDocument doc = mock(SignedBinaryResponseDocument.class);
        SignedBinaryResponseDocument.SignedBinaryResponse inner =
                mock(SignedBinaryResponseDocument.SignedBinaryResponse.class);
        when(doc.getSignedBinaryResponse()).thenReturn(inner);

        when(common.executeOperation(any(), any(), any(), any(), any(), eq(id)))
                .thenReturn(doc);

        var out = service.getSignedBinary(id);
        assertSame(inner, out);
    }

    @Test
    void getStatus_delegatesAndReturnsInner() {
        String externalId = "EXT-9";

        DataStateExternalIdResponseDocument doc = mock(DataStateExternalIdResponseDocument.class);
        DataStateExternalIdResponseDocument.DataStateExternalIdResponse inner =
                mock(DataStateExternalIdResponseDocument.DataStateExternalIdResponse.class);
        when(doc.getDataStateExternalIdResponse()).thenReturn(inner);

        when(common.executeOperation(any(), any(), any(), any(), any(), eq(externalId)))
                .thenReturn(doc);

        var out = service.getStatus(externalId);
        assertSame(inner, out);
    }
}
