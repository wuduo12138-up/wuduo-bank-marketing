package com.wuduo.bank.rights.application.service;

import com.wuduo.bank.rights.api.dto.RightsDefinitionCreateRequest;
import com.wuduo.bank.rights.api.dto.RightsDefinitionResponse;
import com.wuduo.bank.rights.api.dto.RightsDefinitionUpdateRequest;

import java.util.List;

/**
 * Rights Definition Application Service
 */
public interface RightsDefinitionService {

    /**
     * Create a new rights definition
     */
    RightsDefinitionResponse create(RightsDefinitionCreateRequest request);

    /**
     * Update an existing rights definition
     */
    RightsDefinitionResponse update(Long id, RightsDefinitionUpdateRequest request);

    /**
     * Get rights definition by ID
     */
    RightsDefinitionResponse getById(Long id);

    /**
     * Page query rights definitions
     */
    List<RightsDefinitionResponse> page(Integer pageNum, Integer pageSize, Integer type);

    /**
     * Enable a rights definition
     */
    void enable(Long id);

    /**
     * Disable a rights definition
     */
    void disable(Long id);
}
