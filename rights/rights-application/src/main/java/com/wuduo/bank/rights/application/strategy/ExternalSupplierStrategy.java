package com.wuduo.bank.rights.application.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuduo.bank.rights.domain.entity.RightsDefinition;
import com.wuduo.bank.rights.domain.entity.RightsInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * External supplier strategy.
 * Calls the supplier's callback URL via HTTP POST with retry (max 3 attempts).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalSupplierStrategy implements SupplierStrategy {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 3000, 5000};

    @Override
    public Integer supplierType() {
        return 1;
    }

    @Override
    public String issue(RightsDefinition definition, RightsInstance instance) {
        String callbackUrl = definition.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.warn("External supplier has no callback URL for rightsCode={}", definition.getRightsCode());
            return null;
        }

        Map<String, Object> requestBody = Map.of(
                "instanceNo", instance.getInstanceNo(),
                "rightsCode", instance.getRightsCode(),
                "userId", instance.getUserId()
        );

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Calling external supplier {} attempt {}/{}: url={}, instanceNo={}",
                        definition.getSupplierCode(), attempt, MAX_RETRIES, callbackUrl, instance.getInstanceNo());

                String response = restTemplate.postForObject(callbackUrl, requestBody, String.class);
                log.info("External supplier {} responded: {}", definition.getSupplierCode(), response);
                return extractOrderNo(response);

            } catch (Exception e) {
                log.error("External supplier {} attempt {}/{} failed: {}",
                        definition.getSupplierCode(), attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Supplier call interrupted", ie);
                    }
                }
            }
        }

        // All retries exhausted — throw to signal failure to caller
        throw new RuntimeException("External supplier " + definition.getSupplierCode()
                + " failed after " + MAX_RETRIES + " attempts");
    }

    /**
     * Try to extract a supplier order number from the response.
     */
    private String extractOrderNo(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(response, Map.class);
            Object orderNo = map.get("orderNo");
            if (orderNo == null) {
                orderNo = map.get("order_no");
            }
            return orderNo != null ? orderNo.toString() : null;
        } catch (Exception e) {
            log.debug("Could not parse supplier response as JSON, returning raw: {}", response);
            return response.length() > 128 ? response.substring(0, 128) : response;
        }
    }
}
