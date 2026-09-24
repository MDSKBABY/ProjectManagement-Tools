package com.company.projectmanagement.workitem.domain;

/**
 * 关系方向统一为 source → target：父到子、前项到后项、阻塞项到被阻塞项。
 */
public enum WorkItemRelationType {
    PARENT_CHILD,
    PRECEDES,
    BLOCKS
}
