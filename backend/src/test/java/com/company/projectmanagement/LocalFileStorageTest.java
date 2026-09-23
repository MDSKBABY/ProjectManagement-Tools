package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.file.storage.LocalFileStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 不启动 Spring，直接验证本地存储的目录边界。 */
class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void resolvesGeneratedKeysInsideRootAndRejectsTraversalOrAbsolutePaths() {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        String storageKey = storage.generateStorageKey(42L);

        assertThat(storage.resolve(storageKey).startsWith(root.toAbsolutePath())).isTrue();
        assertThatThrownBy(() -> storage.resolve("../outside.bin"))
                .isInstanceOf(ApiException.class)
                .hasMessage("文件存储路径不安全");
        assertThatThrownBy(() -> storage.resolve(root.resolve("absolute.bin").toString()))
                .isInstanceOf(ApiException.class)
                .hasMessage("文件存储路径不安全");
    }
}
