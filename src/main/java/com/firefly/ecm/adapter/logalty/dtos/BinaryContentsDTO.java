package com.firefly.ecm.adapter.logalty.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BinaryContentsDTO {

    private int binaryContentId;
    private int groupId;
    private byte[] contents;
    private String encoding;
    private String filename;
    private String extension;
    private String type;


}
