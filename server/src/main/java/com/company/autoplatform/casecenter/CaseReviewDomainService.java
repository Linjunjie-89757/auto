package com.company.autoplatform.casecenter;

import com.company.autoplatform.ai.AiCaseService;
import com.company.autoplatform.ai.AiReviewResult;
import org.springframework.stereotype.Service;

@Service
public class CaseReviewDomainService {
    private final CaseService caseService;
    private final AiCaseService aiCaseService;

    CaseReviewDomainService(CaseService caseService, AiCaseService aiCaseService) {
        this.caseService = caseService;
        this.aiCaseService = aiCaseService;
    }

    public AiReviewResult aiReviewCase(Long id, String workspaceCode) {
        CaseDetailResponse detail = caseService.getCase(id, workspaceCode);
        return aiCaseService.reviewSavedCase(workspaceCode, detail);
    }
}
