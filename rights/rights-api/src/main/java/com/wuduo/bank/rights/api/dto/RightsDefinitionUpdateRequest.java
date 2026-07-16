package com.wuduo.bank.rights.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Rights Definition Update Request DTO
 */
@Data
public class RightsDefinitionUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private Integer type;

    private Integer supplierType;

    private String supplierCode;

    private Integer totalStock;

    private Integer validDays;

    private String callbackUrl;
}
