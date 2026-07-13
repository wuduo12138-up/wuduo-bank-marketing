package com.wuduo.bank.rights.application.service;

import com.wuduo.bank.rights.api.dto.RightsDefinitionCreateRequest;
import com.wuduo.bank.rights.api.dto.RightsDefinitionResponse;
import com.wuduo.bank.rights.domain.entity.RightsDefinition;

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
    RightsDefinitionResponse update(Long id, RightsDefinitionCreateRequest request);

    /**
     * Get rights definition by ID
     */
    RightsDefinitionResponse getById(Long id);

    /**
     * Page query rights definitions
     */
    // TODO: Define proper page request/response types based on common module
    List<RightsDefinitionResponse> page(Integer pageNum, Integer pageSize);
}
