package com.company.projectmanagement.file.domain;

/** 文件生命周期状态；只有 AVAILABLE 才能被下载。 */
public enum FileStatus {
    RESERVED,
    UPLOADING,
    AVAILABLE,
    FAILED
}
