<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElPagination,
  ElProgress,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import {
  cancelProjectFileUpload,
  completeProjectFileUpload,
  listProjectFiles,
  listProjectFileVersions,
  projectFileDownloadUrl,
  reserveProjectFileMetadata,
  uploadProjectFileChunk,
} from '../api/files'
import type { FileAsset, FileAssetStatus, Pagination } from '../types'

const props = defineProps<{
  projectId: number
  canWrite: boolean
}>()

const files = ref<FileAsset[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const uploadProgress = ref<number | null>(null)
const uploadName = ref('')
const uploadError = ref('')
const uploadController = ref<AbortController | null>(null)
const activeUploadFileId = ref<number | null>(null)
const uploadGroupId = ref<string | undefined>()
const versionsOpen = ref(false)
const versionsLoading = ref(false)
const versions = ref<FileAsset[]>([])

onMounted(() => loadFiles(1))
watch(() => props.projectId, () => loadFiles(1))

async function loadFiles(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listProjectFiles(props.projectId, {
      page,
      pageSize: pagination.pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    files.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '项目资料加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function statusLabel(status: FileAssetStatus): string {
  return {
    RESERVED: '等待上传',
    UPLOADING: '上传中',
    AVAILABLE: '可用',
    FAILED: '失败',
  }[status]
}

function statusType(status: FileAssetStatus): 'info' | 'primary' | 'success' | 'danger' {
  return {
    RESERVED: 'info' as const,
    UPLOADING: 'primary' as const,
    AVAILABLE: 'success' as const,
    FAILED: 'danger' as const,
  }[status]
}

function chooseFile(fileGroupId?: string): void {
  uploadGroupId.value = fileGroupId
  if (fileInput.value) {
    fileInput.value.value = ''
    fileInput.value.click()
  }
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024 * 1024) {
    uploadError.value = '单个文件不能超过 2GB'
    return
  }

  uploadName.value = file.name
  uploadProgress.value = 0
  uploadError.value = ''
  const controller = new AbortController()
  uploadController.value = controller
  try {
    const reservation = await reserveProjectFileMetadata(props.projectId, {
      originalName: file.name,
      mediaType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      fileGroupId: uploadGroupId.value,
    })
    activeUploadFileId.value = reservation.id
    const chunkSize = reservation.chunkSizeBytes ?? 5 * 1024 * 1024
    const totalChunks = reservation.totalChunks ?? Math.ceil(file.size / chunkSize)
    for (let index = 0; index < totalChunks; index += 1) {
      const chunk = file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize))
      await uploadChunkWithRetry(reservation.id, index, chunk, controller.signal)
      uploadProgress.value = Math.round(((index + 1) / Math.max(totalChunks, 1)) * 95)
    }
    await completeProjectFileUpload(props.projectId, reservation.id)
    uploadProgress.value = 100
    await loadFiles(1)
  } catch (reason) {
    if (controller.signal.aborted) {
      uploadError.value = '上传已取消，可重新选择文件继续'
    } else {
      uploadError.value = reason instanceof ApiError ? reason.message : '文件上传失败，请重试'
    }
  } finally {
    uploadController.value = null
    activeUploadFileId.value = null
    uploadGroupId.value = undefined
  }
}

async function uploadChunkWithRetry(
  fileId: number,
  chunkIndex: number,
  chunk: Blob,
  signal: AbortSignal,
): Promise<void> {
  let lastError: unknown
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await uploadProjectFileChunk(props.projectId, fileId, chunkIndex, chunk, signal)
      return
    } catch (reason) {
      if (signal.aborted) throw reason
      lastError = reason
    }
  }
  throw lastError
}

async function cancelUpload(): Promise<void> {
  uploadController.value?.abort()
  if (activeUploadFileId.value !== null) {
    try {
      await cancelProjectFileUpload(props.projectId, activeUploadFileId.value)
      await loadFiles(1)
    } catch (reason) {
      uploadError.value = reason instanceof ApiError ? reason.message : '取消上传失败，请稍后重试'
    }
  }
}

async function showVersions(row: unknown): Promise<void> {
  const file = row as FileAsset
  versionsOpen.value = true
  versionsLoading.value = true
  uploadError.value = ''
  try {
    versions.value = await listProjectFileVersions(props.projectId, file.fileGroupId)
  } catch (reason) {
    uploadError.value = reason instanceof ApiError ? reason.message : '版本记录加载失败'
  } finally {
    versionsLoading.value = false
  }
}
</script>

<template>
  <section class="subpanel" aria-labelledby="project-files-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">项目资料</p>
        <h2 id="project-files-title">文件与版本</h2>
      </div>
      <div v-if="canWrite" class="upload-entry">
        <ElButton
          data-test="upload-entry"
          type="primary"
          :disabled="uploadController !== null"
          @click="chooseFile()"
        >上传资料</ElButton>
        <small>单文件最大 2GB，自动分片并校验完整性</small>
        <input ref="fileInput" class="visually-hidden" type="file" @change="onFileSelected" />
      </div>
    </div>

    <div v-if="uploadProgress !== null" class="upload-progress" aria-live="polite">
      <div>
        <strong>{{ uploadName }}</strong>
        <span>{{ uploadProgress === 100 ? '上传完成' : '正在上传' }}</span>
      </div>
      <ElProgress :percentage="uploadProgress" />
      <ElButton v-if="uploadController" size="small" @click="cancelUpload">取消上传</ElButton>
    </div>
    <p v-if="uploadError" class="content-error" role="alert">{{ uploadError }}</p>

    <form class="member-filter" aria-label="项目资料搜索" @submit.prevent="loadFiles(1)">
      <ElInput v-model="keyword" clearable placeholder="按文件名搜索" aria-label="搜索项目资料" />
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">
      {{ error }}
      <button type="button" @click="loadFiles()">重试</button>
    </p>

    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="files" empty-text="暂无项目资料">
        <ElTableColumn label="文件" min-width="230">
          <template #default="scope">
            <strong>{{ scope.row.originalName }}</strong>
            <span class="cell-secondary">版本 {{ scope.row.version }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="大小" width="110">
          <template #default="scope">{{ formatBytes(scope.row.sizeBytes) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="120">
          <template #default="scope">
            <ElTag :type="statusType(scope.row.status)" effect="plain">
              {{ statusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="uploadedBy.displayName" label="上传人" min-width="120" />
        <ElTableColumn prop="createdAt" label="创建时间" min-width="180" />
        <ElTableColumn label="操作" min-width="210" fixed="right">
          <template #default="scope">
            <a
              v-if="scope.row.status === 'AVAILABLE'"
              class="table-link"
              :href="projectFileDownloadUrl(projectId, scope.row.id)"
            >下载</a>
            <ElButton link type="primary" @click="showVersions(scope.row)">版本</ElButton>
            <ElButton
              v-if="canWrite && scope.row.status === 'AVAILABLE'"
              link
              type="primary"
              @click="chooseFile(scope.row.fileGroupId)"
            >上传新版本</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无项目资料" :image-size="64" />
        </template>
      </ElTable>
    </div>

    <ElPagination
      v-if="pagination.totalPages > 1"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.totalItems"
      @current-change="loadFiles"
    />

    <ElDialog v-model="versionsOpen" title="文件版本" width="min(36rem, 92vw)">
      <ElTable :data="versions" :aria-busy="versionsLoading" empty-text="暂无版本">
        <ElTableColumn prop="version" label="版本" width="80" />
        <ElTableColumn prop="originalName" label="文件名" min-width="180" />
        <ElTableColumn label="状态" width="110">
          <template #default="scope">{{ statusLabel(scope.row.status) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="80">
          <template #default="scope">
            <a
              v-if="scope.row.status === 'AVAILABLE'"
              class="table-link"
              :href="projectFileDownloadUrl(projectId, scope.row.id)"
            >下载</a>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>
  </section>
</template>
