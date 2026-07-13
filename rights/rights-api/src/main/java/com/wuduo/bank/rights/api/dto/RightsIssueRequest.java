package com.wuduo.bank.rights.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * Rights Issue Request DTO
 */
@Data
public class RightsIssueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "权益编码不能为空")
    private String rightsCode;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotNull(message = "来源类型不能为空")
    private Integer sourceType;

    private String sourceNo;
}
