package com.wuduo.bank.rights.application.service;

import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import com.wuduo.bank.rights.api.dto.RightsIssueResponse;

import java.util.List;

/**
 * Rights Issue Application Service
 */
public interface RightsIssueService {

    /**
     * Issue rights to a user
     */
    RightsIssueResponse issue(RightsIssueRequest request);

    /**
     * Get rights instance by instance number
     */
    RightsInstanceResponse getByInstanceNo(String instanceNo);

    /**
     * Get rights instances by user ID
     */
    List<RightsInstanceResponse> getByUserId(String userId);

    /**
     * Activate a rights instance
     */
    RightsInstanceResponse activate(String instanceNo);

    /**
     * Use a rights instance
     */
    RightsInstanceResponse use(String instanceNo);

    /**
     * Revoke a rights instance (refund stock)
     */
    RightsInstanceResponse revoke(String instanceNo);
}
