package com.wuduo.bank.rights.application.service.impl;

import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import com.wuduo.bank.rights.application.service.RightsIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rights Issue Application Service Implementation
 */
@Service
@RequiredArgsConstructor
public class RightsIssueServiceImpl implements RightsIssueService {

    // TODO: Inject mapper/repository dependencies

    @Override
    public RightsInstanceResponse issue(RightsIssueRequest request) {
        // TODO: Implement rights issue logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public RightsInstanceResponse getByInstanceNo(String instanceNo) {
        // TODO: Implement get rights instance by instance number logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public List<RightsInstanceResponse> getByUserId(String userId) {
        // TODO: Implement get rights instances by user ID logic
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
