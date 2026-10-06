<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import { listProjectFiles } from '../api/files'
import {
  createKnowledgeArticle,
  deleteKnowledgeArticle,
  getKnowledgeArticle,
  listKnowledgeArticles,
  reviewKnowledgeArticle,
  submitKnowledgeArticle,
  updateKnowledgeArticle,
} from '../api/knowledge-articles'
import type {
  FileAsset,
  KnowledgeArticle,
  KnowledgeArticleStatus,
  KnowledgeArticleSummary,
  Pagination,
  SaveKnowledgeArticleInput,
} from '../types'
import KnowledgeArticleEditorDialog from './KnowledgeArticleEditorDialog.vue'

const props = defineProps<{
  projectId: number
  currentUserId: number
  canWrite: boolean
  canReview: boolean
}>()

const articles = ref<KnowledgeArticleSummary[]>([])
const files = ref<FileAsset[]>([])
const selectedArticle = ref<KnowledgeArticle | null>(null)
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({ keyword: '', status: '' as '' | KnowledgeArticleStatus, tag: '' })
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const error = ref('')
const editorError = ref('')
const editorOpen = ref(false)
const editingArticle = ref<KnowledgeArticle | null>(null)
const reviewComment = ref('')

const canEditSelected = computed(() => Boolean(
  props.canWrite && selectedArticle.value
  && (props.canReview || selectedArticle.value.createdBy === props.currentUserId)
  && ['DRAFT', 'REJECTED'].includes(selectedArticle.value.status),
))

onMounted(load)
watch(() => props.projectId, () => load(1))

async function load(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [result, fileResult] = await Promise.all([
      listKnowledgeArticles(props.projectId, {
        page,
        pageSize: pagination.pageSize,
        keyword: filters.keyword.trim() || undefined,
        status: filters.status || undefined,
        tag: filters.tag.trim() || undefined,
      }),
      props.canWrite
        ? listProjectFiles(props.projectId, { page: 1, pageSize: 100 })
        : Promise.resolve(null),
    ])
    articles.value = result.data
    Object.assign(pagination, result.pagination)
    if (fileResult) files.value = fileResult.data.filter(({ status }) => status === 'AVAILABLE')
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function selectArticle(row: KnowledgeArticleSummary | null): Promise<void> {
  if (!row) {
    selectedArticle.value = null
    return
  }
  detailLoading.value = true
  error.value = ''
  try {
    selectedArticle.value = await getKnowledgeArticle(props.projectId, row.id)
    reviewComment.value = selectedArticle.value.reviewComment ?? ''
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    detailLoading.value = false
  }
}

function openCreate(): void {
  editingArticle.value = null
  editorError.value = ''
  editorOpen.value = true
}

function openEdit(): void {
  if (!selectedArticle.value || !canEditSelected.value) return
  editingArticle.value = selectedArticle.value
  editorError.value = ''
  editorOpen.value = true
}

async function save(input: SaveKnowledgeArticleInput): Promise<void> {
  saving.value = true
  editorError.value = ''
  try {
    const saved = editingArticle.value
      ? await updateKnowledgeArticle(props.projectId, editingArticle.value.id, input)
      : await createKnowledgeArticle(props.projectId, input)
    editorOpen.value = false
    selectedArticle.value = saved
    await load(editingArticle.value ? pagination.page : 1)
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function submitForReview(): Promise<void> {
  if (!selectedArticle.value || !canEditSelected.value) return
  await runAction(async () => {
    selectedArticle.value = await submitKnowledgeArticle(props.projectId, selectedArticle.value!.id)
  })
}

async function review(status: 'APPROVED' | 'REJECTED'): Promise<void> {
  if (!selectedArticle.value || !props.canReview) return
  if (status === 'REJECTED' && !reviewComment.value.trim()) {
    error.value = '驳回时请填写审核意见'
    return
  }
  await runAction(async () => {
    selectedArticle.value = await reviewKnowledgeArticle(
      props.projectId,
      selectedArticle.value!.id,
      { status, comment: reviewComment.value.trim() || undefined },
    )
  })
}

async function remove(): Promise<void> {
  if (!selectedArticle.value || !canEditSelected.value) return
  const id = selectedArticle.value.id
  await runAction(async () => {
    await deleteKnowledgeArticle(props.projectId, id)
    selectedArticle.value = null
  })
}

async function runAction(action: () => Promise<void>): Promise<void> {
  saving.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

function statusLabel(status: KnowledgeArticleStatus): string {
  return { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status]
}

function statusType(status: KnowledgeArticleStatus): 'info' | 'warning' | 'success' | 'danger' {
  return { DRAFT: 'info' as const, PENDING_REVIEW: 'warning' as const,
    APPROVED: 'success' as const, REJECTED: 'danger' as const }[status]
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '知识库操作失败，请稍后重试'
}
</script>

<template>
  <section class="subpanel knowledge-panel" aria-labelledby="knowledge-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">实施经验沉淀</p>
        <h2 id="knowledge-title">技术知识库</h2>
        <p>记录场景、现象、原因和可复用的解决办法。</p>
      </div>
      <ElButton
        v-if="canWrite"
        data-test="create-knowledge-article"
        type="primary"
        @click="openCreate"
      >新建知识</ElButton>
    </div>

    <form class="knowledge-filters" aria-label="技术知识筛选" @submit.prevent="load(1)">
      <ElInput v-model="filters.keyword" clearable placeholder="标题、场景或正文关键词" aria-label="搜索技术知识" />
      <ElSelect v-model="filters.status" aria-label="审核状态">
        <ElOption label="全部状态" value="" />
        <ElOption label="草稿" value="DRAFT" />
        <ElOption label="待审核" value="PENDING_REVIEW" />
        <ElOption label="已通过" value="APPROVED" />
        <ElOption label="已驳回" value="REJECTED" />
      </ElSelect>
      <ElInput v-model="filters.tag" clearable placeholder="精确标签" aria-label="知识标签" />
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="articles" highlight-current-row @current-change="selectArticle">
        <ElTableColumn label="知识" min-width="260">
          <template #default="scope">
            <strong>{{ scope.row.title }}</strong>
            <span class="cell-secondary">{{ scope.row.scenario }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="scope">
            <ElTag :type="statusType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建人" prop="createdByDisplayName" width="120" />
        <ElTableColumn label="附件" width="80">
          <template #default="scope">{{ scope.row.attachmentCount }}</template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" min-width="170">
          <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无技术知识" :image-size="64" /></template>
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
      @current-change="load"
    />

    <section v-if="selectedArticle" class="knowledge-detail" :aria-busy="detailLoading" aria-label="技术知识详情">
      <div class="subpanel-heading">
        <div>
          <p class="section-label">知识详情</p>
          <h3>{{ selectedArticle.title }}</h3>
        </div>
        <div class="detail-actions">
          <ElButton v-if="canEditSelected" @click="openEdit">编辑草稿</ElButton>
          <ElButton v-if="canEditSelected" type="primary" :loading="saving" @click="submitForReview">提交审核</ElButton>
          <ElPopconfirm
            v-if="canEditSelected"
            title="确认删除该知识草稿？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="remove"
          >
            <template #reference><ElButton type="danger" plain>删除</ElButton></template>
          </ElPopconfirm>
        </div>
      </div>
      <dl class="knowledge-content">
        <div><dt>适用场景</dt><dd>{{ selectedArticle.scenario }}</dd></div>
        <div><dt>问题现象</dt><dd>{{ selectedArticle.symptom }}</dd></div>
        <div><dt>问题原因</dt><dd>{{ selectedArticle.cause }}</dd></div>
        <div><dt>解决办法</dt><dd>{{ selectedArticle.solution }}</dd></div>
        <div><dt>适用条件</dt><dd>{{ selectedArticle.applicableConditions || '未设置' }}</dd></div>
        <div><dt>标签</dt><dd>{{ selectedArticle.tags.join('、') || '未设置' }}</dd></div>
      </dl>
      <div class="knowledge-attachments">
        <strong>附件</strong>
        <ul v-if="selectedArticle.attachments.length">
          <li v-for="file in selectedArticle.attachments" :key="file.id">
            <a :href="`/api/v1/projects/${projectId}/files/${file.id}/download`">{{ file.originalName }}</a>
            <small>{{ formatBytes(file.sizeBytes) }}</small>
          </li>
        </ul>
        <p v-else>暂无附件</p>
      </div>
      <div v-if="selectedArticle.reviewComment" class="review-result">
        <strong>审核意见</strong>
        <p>{{ selectedArticle.reviewComment }}</p>
        <small v-if="selectedArticle.reviewedByDisplayName">审核人：{{ selectedArticle.reviewedByDisplayName }}</small>
      </div>
      <div v-if="canReview && selectedArticle.status === 'PENDING_REVIEW'" class="review-actions">
        <ElInput v-model="reviewComment" type="textarea" :rows="2" maxlength="2000" placeholder="审核意见；驳回时必填" />
        <div>
          <ElButton type="danger" plain :loading="saving" @click="review('REJECTED')">驳回</ElButton>
          <ElButton data-test="approve-knowledge-article" type="success" :loading="saving" @click="review('APPROVED')">通过</ElButton>
        </div>
      </div>
    </section>

    <KnowledgeArticleEditorDialog
      :open="editorOpen"
      :article="editingArticle"
      :files="files"
      :saving="saving"
      :error="editorError"
      @close="editorOpen = false"
      @submit="save"
    />
  </section>
</template>

<style scoped>
.knowledge-filters {
  display: grid;
  grid-template-columns: minmax(16rem, 2fr) minmax(9rem, 1fr) minmax(10rem, 1fr) auto;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.knowledge-detail {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color, #e5e7eb);
}

.knowledge-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.knowledge-content div:nth-child(2),
.knowledge-content div:nth-child(3),
.knowledge-content div:nth-child(4) {
  grid-column: 1 / -1;
}

.knowledge-content dt {
  margin-bottom: 0.25rem;
  color: #64748b;
  font-size: 0.8125rem;
}

.knowledge-content dd {
  margin: 0;
  white-space: pre-wrap;
}

.knowledge-attachments,
.review-result,
.review-actions {
  margin-top: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.knowledge-attachments ul { margin: 0.5rem 0 0; padding-left: 1.25rem; }
.knowledge-attachments li { display: flex; gap: 0.75rem; margin-top: 0.25rem; }
.knowledge-attachments small { color: #64748b; }
.review-actions > div { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 0.75rem; }

@media (max-width: 768px) {
  .knowledge-filters,
  .knowledge-content { grid-template-columns: 1fr; }
  .knowledge-content div:nth-child(n) { grid-column: auto; }
}
</style>
