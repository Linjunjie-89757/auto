package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BugCommentDomainService {

    private final BugDomainService bugDomainService;
    private final BugCommentMapper bugCommentMapper;
    private final WorkspaceService workspaceService;

    public BugCommentDomainService(
            BugDomainService bugDomainService,
            BugCommentMapper bugCommentMapper,
            WorkspaceService workspaceService
    ) {
        this.bugDomainService = bugDomainService;
        this.bugCommentMapper = bugCommentMapper;
        this.workspaceService = workspaceService;
    }

    public List<BugCommentEntity> listComments(Long id, String workspaceCode) {
        BugEntity entity = bugDomainService.requireBug(id);
        bugDomainService.validateReadable(entity, workspaceCode);
        return listCommentEntities(id);
    }

    public BugCommentEntity addComment(Long id, String workspaceCode, CreateBugCommentRequest request) {
        BugEntity entity = bugDomainService.requireBug(id);
        bugDomainService.validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        BugCommentEntity comment = new BugCommentEntity();
        comment.setBugId(id);
        comment.setContent(request.content());
        comment.setCommenterId(CurrentUserContext.get());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        bugCommentMapper.insert(comment);
        return comment;
    }

    private List<BugCommentEntity> listCommentEntities(Long bugId) {
        return bugCommentMapper.selectList(new LambdaQueryWrapper<BugCommentEntity>()
                .eq(BugCommentEntity::getBugId, bugId)
                .orderByAsc(BugCommentEntity::getId));
    }
}
