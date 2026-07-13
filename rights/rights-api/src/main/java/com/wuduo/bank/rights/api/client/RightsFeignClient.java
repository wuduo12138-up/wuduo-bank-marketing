package com.wuduo.bank.rights.api.client;

import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Rights Feign Client
 */
@FeignClient(name = "rights", contextId = "rightsFeignClient")
public interface RightsFeignClient {

    /**
     * Issue rights to a user
     */
    @PostMapping("/api/v1/rights/instances/issue")
    RightsInstanceResponse issue(@RequestBody RightsIssueRequest request);

    /**
     * Get rights instance by instance number
     */
    @GetMapping("/api/v1/rights/instances/getByInstanceNo")
    RightsInstanceResponse getByInstanceNo(@RequestParam("instanceNo") String instanceNo);

    /**
     * Get rights instances by user ID
     */
    @GetMapping("/api/v1/rights/instances/getByUserId")
    List<RightsInstanceResponse> getByUserId(@RequestParam("userId") String userId);
}
