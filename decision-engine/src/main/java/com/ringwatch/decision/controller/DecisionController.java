package com.ringwatch.decision.controller;

import com.ringwatch.common.security.AuthenticatedPrincipal;
import com.ringwatch.decision.controller.dto.DecisionResponse;
import com.ringwatch.decision.controller.dto.OverrideRequest;
import com.ringwatch.decision.model.Decision;
import com.ringwatch.decision.service.DecisionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping("/transactions/{transactionId}/override")
    public DecisionResponse override(
            @PathVariable("transactionId") String transactionId,
            @Valid @RequestBody OverrideRequest request,
            Authentication authentication) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        Decision decision = decisionService.override(
                transactionId, request.outcome(), request.reason(), principal.username());
        return DecisionResponse.from(decision);
    }
}
