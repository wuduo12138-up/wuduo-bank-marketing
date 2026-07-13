package com.wuduo.bank.rights.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * Rights Definition Create Request DTO
 */
@Data
public class RightsDefinitionCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "权益名称不能为空")
    private String name;

    @NotNull(message = "权益类型不能为空")
    private Integer type;

    @NotNull(message = "供应商类型不能为空")
    private Integer supplierType;

    @NotBlank(message = "供应商编码不能为空")
    private String supplierCode;

    @NotNull(message = "总库存不能为空")
    private Integer totalStock;

    @NotNull(message = "有效天数不能为空")
    private Integer validDays;

    private String callbackUrl;
}
