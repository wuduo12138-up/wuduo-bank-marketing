package com.wuduo.bank.rights.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Rights Definition Response DTO
 */
@Data
public class RightsDefinitionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String rightsCode;

    private String name;

    private Integer type;

    private Integer supplierType;

    private String supplierCode;

    private Integer totalStock;

    private Integer usedStock;

    private Integer validDays;

    private Integer status;
}
