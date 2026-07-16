package com.wuduo.bank.rights.boot.controller;

import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import com.wuduo.bank.rights.api.dto.RightsIssueResponse;
import com.wuduo.bank.rights.application.service.RightsIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rights Instance Controller
 */
@RestController
@RequestMapping("/api/v1/rights/instances")
@RequiredArgsConstructor
public class RightsInstanceController {

    private final RightsIssueService rightsIssueService;

    /**
     * Issue rights to a user
     */
    @PostMapping("/issue")
    public RightsIssueResponse issue(@Valid @RequestBody RightsIssueRequest request) {
        return rightsIssueService.issue(request);
    }

    /**
     * Get rights instance by instance number
     */
    @GetMapping("/getByInstanceNo")
    public RightsInstanceResponse getByInstanceNo(@RequestParam String instanceNo) {
        return rightsIssueService.getByInstanceNo(instanceNo);
    }

    /**
     * Get rights instances by user ID
     */
    @GetMapping("/getByUserId")
    public List<RightsInstanceResponse> getByUserId(@RequestParam String userId) {
        return rightsIssueService.getByUserId(userId);
    }

    /**
     * Activate a rights instance
     */
    @PutMapping("/{instanceNo}/activate")
    public RightsInstanceResponse activate(@PathVariable String instanceNo) {
        return rightsIssueService.activate(instanceNo);
    }

    /**
     * Use a rights instance
     */
    @PutMapping("/{instanceNo}/use")
    public RightsInstanceResponse use(@PathVariable String instanceNo) {
        return rightsIssueService.use(instanceNo);
    }

    /**
     * Revoke a rights instance
     */
    @PutMapping("/{instanceNo}/revoke")
    public RightsInstanceResponse revoke(@PathVariable String instanceNo) {
        return rightsIssueService.revoke(instanceNo);
    }
}
