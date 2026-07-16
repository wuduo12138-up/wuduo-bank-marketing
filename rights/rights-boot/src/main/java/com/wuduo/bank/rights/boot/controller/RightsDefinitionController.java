package com.wuduo.bank.rights.boot.controller;

import com.wuduo.bank.rights.api.dto.RightsDefinitionCreateRequest;
import com.wuduo.bank.rights.api.dto.RightsDefinitionResponse;
import com.wuduo.bank.rights.api.dto.RightsDefinitionUpdateRequest;
import com.wuduo.bank.rights.application.service.RightsDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rights Definition Controller
 */
@RestController
@RequestMapping("/api/v1/rights/definitions")
@RequiredArgsConstructor
public class RightsDefinitionController {

    private final RightsDefinitionService rightsDefinitionService;

    /**
     * Create a new rights definition
     */
    @PostMapping
    public RightsDefinitionResponse create(@Valid @RequestBody RightsDefinitionCreateRequest request) {
        return rightsDefinitionService.create(request);
    }

    /**
     * Update an existing rights definition
     */
    @PutMapping("/{id}")
    public RightsDefinitionResponse update(@PathVariable Long id,
                                           @RequestBody RightsDefinitionUpdateRequest request) {
        return rightsDefinitionService.update(id, request);
    }

    /**
     * Get rights definition by ID
     */
    @GetMapping("/{id}")
    public RightsDefinitionResponse getById(@PathVariable Long id) {
        return rightsDefinitionService.getById(id);
    }

    /**
     * Page query rights definitions
     */
    @GetMapping("/page")
    public List<RightsDefinitionResponse> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) Integer type) {
        return rightsDefinitionService.page(pageNum, pageSize, type);
    }

    /**
     * Enable a rights definition
     */
    @PutMapping("/{id}/enable")
    public void enable(@PathVariable Long id) {
        rightsDefinitionService.enable(id);
    }

    /**
     * Disable a rights definition
     */
    @PutMapping("/{id}/disable")
    public void disable(@PathVariable Long id) {
        rightsDefinitionService.disable(id);
    }
}
