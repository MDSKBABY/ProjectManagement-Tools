package com.company.projectmanagement.workitem.domain;

/** 个人提醒生命周期；提醒被关闭后仍保留，便于用户回看。 */
public enum WorkItemReminderStatus {
    PENDING,
    DISMISSED
}
