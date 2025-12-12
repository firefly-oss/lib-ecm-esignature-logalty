package com.firefly.ecm.adapter.logalty.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceiverDTO {

    private String name;
    private String last1;
    private String last2;
    private String telephone;
    private String email;

    private String documentType;
    private String documentCountry;
    private String documentNumber;

    private int receiverId;
    private int groupId;
    private int ruleId;

    private String uuid;

}
