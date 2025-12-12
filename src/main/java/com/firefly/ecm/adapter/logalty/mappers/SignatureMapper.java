package com.firefly.ecm.adapter.logalty.mappers;

import com.firefly.core.ecm.domain.model.esignature.SignatureRequest;
import com.firefly.ecm.adapter.logalty.dtos.ReceiverDTO;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mapper utilities for signature-related DTO conversions.
 */
public final class SignatureMapper {

    private SignatureMapper() {
        // utility class
    }

    public static List<ReceiverDTO> toReceiverDTOs(List<SignatureRequest> signatureRequests) {
        AtomicInteger counter = new AtomicInteger(1);

        return signatureRequests.stream()
                .map(req -> {
                    ReceiverDTO dto = new ReceiverDTO();

                    dto.setName(req.getSignerName());
                    dto.setLast1("");
                    dto.setLast2("");
                    dto.setTelephone("");
                    dto.setEmail(req.getSignerEmail());

                    dto.setDocumentType("");
                    dto.setDocumentCountry("");
                    dto.setDocumentNumber("");

                    dto.setUuid(req.getSignerId() != null ? req.getSignerId().toString() : null);

                    dto.setReceiverId(counter.getAndIncrement());
                    dto.setGroupId(1);
                    dto.setRuleId(1);

                    return dto;
                })
                .toList();
    }
}
