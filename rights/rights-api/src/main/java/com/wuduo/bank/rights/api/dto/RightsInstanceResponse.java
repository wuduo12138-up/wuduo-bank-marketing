package com.wuduo.bank.rights.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Rights Instance Response DTO
 */
@Data
public class RightsInstanceResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String instanceNo;

    private String rightsCode;

    private String userId;

    private Integer sourceType;

    private String sourceNo;

    private Integer status;

    private LocalDateTime activateTime;

    private LocalDateTime expireTime;

    private LocalDateTime useTime;
}
