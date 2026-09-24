<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
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
import { listProjectMembers } from '../api/projects'
import {
  createWorkItem,
  createWorkItemRelation,
  deleteWorkItem,
  deleteWorkItemRelation,
  getWorkItem,
  listWorkItemRelations,
  listWorkItemStatusHistory,
  listWorkItems,
  transitionWorkItemStatus,
  updateWorkItem,
} from '../api/work-items'
import type {
  CreateWorkItemInput,
  Pagination,
  ProjectMember,
  UpdateWorkItemInput,
  WorkItem,
  WorkItemPriority,
  WorkItemRelation,
  WorkItemRelationType,
  WorkItemStatus,
  WorkItemStatusLog,
  WorkItemType,
} from '../types'
import WorkItemBoard from './WorkItemBoard.vue'
import WorkItemCalendar from './WorkItemCalendar.vue'
import WorkItemReminderPanel from './WorkItemReminderPanel.vue'

const props = defineProps<{
  projectId: number
  currentUserId: number
  isAdministrator: boolean
  canWrite: boolean
  canDelete: boolean
}>()

const items = ref<WorkItem[]>([])
const viewMode = ref<'LIST' | 'BOARD' | 'CALENDAR'>('LIST')
const visualRefreshKey = ref(0)
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({
  keyword: '',
  type: '' as '' | WorkItemType,
  status: '' as '' | WorkItemStatus,
  priority: '' as '' | WorkItemPriority,
  assigneeId: null as number | null,
  plannedFrom: '',
  plannedTo: '',
})
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const error = ref('')
const detailError = ref('')
const selectedItem = ref<WorkItem | null>(null)
const history = ref<WorkItemStatusLog[]>([])
const relations = ref<WorkItemRelation[]>([])

const editorOpen = ref(false)
const editing = ref(false)
const saving = ref(false)
const editorError = ref('')
const editorForm = reactive({
  type: 'TASK' as WorkItemType,
  title: '',
  description: '',
  priority: 'NORMAL' as WorkItemPriority,
  assigneeId: null as number | null,
  plannedStartDate: '',
  plannedEndDate: '',
})

const transitionOpen = ref(false)
const transitioning = ref(false)
const transitionError = ref('')
const transitionForm = reactive({ status: '' as '' | WorkItemStatus, comment: '' })

const relationOpen = ref(false)
const relationSaving = ref(false)
const relationError = ref('')
const relationCandidates = ref<WorkItem[]>([])
const relationForm = reactive({
  type: 'PARENT_CHILD' as WorkItemRelationType,
  direction: 'SOURCE' as 'SOURCE' | 'TARGET',
  relatedWorkItemId: null as number | null,
})

const currentMember = computed(() =>
  members.value.find(({ userId }) => userId === props.currentUserId),
)
const canManage = computed(() =>
  props.isAdministrator || ['OWNER', 'MANAGER'].includes(currentMember.value?.role ?? ''),
)
const canCreate = computed(() => props.canWrite && canManage.value)
const canEditSelected = computed(() =>
  props.canWrite && Boolean(selectedItem.value) && (
    canManage.value || selectedItem.value?.assignee?.id === props.currentUserId
  ),
)
const canDeleteSelected = computed(() => props.canDelete && canManage.value)
const canManageRelations = computed(() => props.canWrite && canManage.value)
const assignableMembers = computed(() =>
  members.value.filter(({ role, status }) => status === 'ACTIVE' && role !== 'VIEWER'),
)
const transitionTargets = computed(() =>
  selectedItem.value ? allowedTargets(selectedItem.value.status) : [],
)
const editorReady = computed(() => {
  if (!editorForm.title.trim()) return false
  return !editorForm.plannedStartDate || !editorForm.plannedEndDate
    || editorForm.plannedEndDate >= editorForm.plannedStartDate
})

onMounted(() => loadProjectData())
watch(() => props.projectId, () => loadProjectData())

async function loadProjectData(): Promise<void> {
  selectedItem.value = null
  history.value = []
  relations.value = []
  await Promise.all([loadItems(1), loadMembers()])
}

async function loadItems(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listWorkItems(props.projectId, {
      page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword.trim() || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      assigneeId: filters.assigneeId ?? undefined,
      plannedFrom: filters.plannedFrom || undefined,
      plannedTo: filters.plannedTo || undefined,
    })
    items.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function loadMembers(): Promise<void> {
  try {
    const result = await listProjectMembers(props.projectId, { page: 1, pageSize: 100 })
    members.value = result.data
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

async function selectWorkItem(row: WorkItem | null): Promise<void> {
  if (!row) {
    selectedItem.value = null
    return
  }
  await loadDetail(row.id)
}

async function selectFromVisual(item: WorkItem): Promise<void> {
  await loadDetail(item.id)
}

async function transitionFromBoard(item: WorkItem): Promise<void> {
  await loadDetail(item.id)
  openTransition()
}

async function loadDetail(workItemId: number): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    const [item, statusHistory, relationPage] = await Promise.all([
      getWorkItem(props.projectId, workItemId),
      listWorkItemStatusHistory(props.projectId, workItemId),
      listWorkItemRelations(props.projectId, {
        page: 1,
        pageSize: 100,
        workItemId,
      }),
    ])
    selectedItem.value = item
    history.value = statusHistory
    relations.value = relationPage.data
  } catch (reason) {
    detailError.value = errorMessage(reason)
  } finally {
    detailLoading.value = false
  }
}

function resetEditor(): void {
  Object.assign(editorForm, {
    type: 'TASK',
    title: '',
    description: '',
    priority: 'NORMAL',
    assigneeId: null,
    plannedStartDate: '',
    plannedEndDate: '',
  })
  editorError.value = ''
}

function openCreate(): void {
  resetEditor()
  editing.value = false
  editorOpen.value = true
}

function openEdit(): void {
  if (!selectedItem.value) return
  resetEditor()
  editing.value = true
  Object.assign(editorForm, {
    type: selectedItem.value.type,
    title: selectedItem.value.title,
    description: selectedItem.value.description ?? '',
    priority: selectedItem.value.priority,
    assigneeId: selectedItem.value.assignee?.id ?? null,
    plannedStartDate: selectedItem.value.plannedStartDate ?? '',
    plannedEndDate: selectedItem.value.plannedEndDate ?? '',
  })
  editorOpen.value = true
}

async function saveEditor(): Promise<void> {
  if (!editorReady.value) return
  saving.value = true
  editorError.value = ''
  try {
    let saved: WorkItem
    if (editing.value && selectedItem.value) {
      const input: UpdateWorkItemInput = {
        title: editorForm.title.trim(),
        description: editorForm.description.trim() || null,
        priority: editorForm.priority,
        plannedStartDate: editorForm.plannedStartDate || null,
        plannedEndDate: editorForm.plannedEndDate || null,
      }
      if (canManage.value) {
        input.type = editorForm.type
        input.assigneeId = editorForm.assigneeId
      }
      saved = await updateWorkItem(props.projectId, selectedItem.value.id, input)
    } else {
      const input: CreateWorkItemInput = {
        type: editorForm.type,
        title: editorForm.title.trim(),
        description: editorForm.description.trim() || undefined,
        priority: editorForm.priority,
        assigneeId: editorForm.assigneeId ?? undefined,
        plannedStartDate: editorForm.plannedStartDate || undefined,
        plannedEndDate: editorForm.plannedEndDate || undefined,
      }
      saved = await createWorkItem(props.projectId, input)
    }
    editorOpen.value = false
    await loadItems(editing.value ? pagination.page : 1)
    visualRefreshKey.value += 1
    await loadDetail(saved.id)
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function removeSelected(): Promise<void> {
  if (!selectedItem.value) return
  const workItemId = selectedItem.value.id
  error.value = ''
  try {
    await deleteWorkItem(props.projectId, workItemId)
    selectedItem.value = null
    history.value = []
    relations.value = []
    await loadItems(1)
    visualRefreshKey.value += 1
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

function openTransition(): void {
  transitionForm.status = transitionTargets.value[0] ?? ''
  transitionForm.comment = ''
  transitionError.value = ''
  transitionOpen.value = true
}

async function saveTransition(): Promise<void> {
  if (!selectedItem.value || !transitionForm.status) return
  transitioning.value = true
  transitionError.value = ''
  try {
    const saved = await transitionWorkItemStatus(props.projectId, selectedItem.value.id, {
      status: transitionForm.status,
      comment: transitionForm.comment.trim() || undefined,
    })
    transitionOpen.value = false
    await loadItems(pagination.page)
    visualRefreshKey.value += 1
    await loadDetail(saved.id)
  } catch (reason) {
    transitionError.value = errorMessage(reason)
  } finally {
    transitioning.value = false
  }
}

async function openRelationEditor(): Promise<void> {
  if (!selectedItem.value) return
  Object.assign(relationForm, {
    type: 'PARENT_CHILD',
    direction: 'SOURCE',
    relatedWorkItemId: null,
  })
  relationError.value = ''
  relationOpen.value = true
  try {
    const result = await listWorkItems(props.projectId, { page: 1, pageSize: 100 })
    relationCandidates.value = result.data.filter(({ id }) => id !== selectedItem.value?.id)
  } catch (reason) {
    relationError.value = errorMessage(reason)
  }
}

async function saveRelation(): Promise<void> {
  if (!selectedItem.value || !relationForm.relatedWorkItemId) return
  relationSaving.value = true
  relationError.value = ''
  try {
    const currentId = selectedItem.value.id
    const relatedId = relationForm.relatedWorkItemId
    await createWorkItemRelation(props.projectId, {
      sourceWorkItemId: relationForm.direction === 'SOURCE' ? currentId : relatedId,
      targetWorkItemId: relationForm.direction === 'SOURCE' ? relatedId : currentId,
      type: relationForm.type,
    })
    relationOpen.value = false
    await loadRelations(currentId)
  } catch (reason) {
    relationError.value = errorMessage(reason)
  } finally {
    relationSaving.value = false
  }
}

async function removeRelation(relationId: number): Promise<void> {
  if (!selectedItem.value) return
  detailError.value = ''
  try {
    await deleteWorkItemRelation(props.projectId, relationId)
    await loadRelations(selectedItem.value.id)
  } catch (reason) {
    detailError.value = errorMessage(reason)
  }
}

async function loadRelations(workItemId: number): Promise<void> {
  const result = await listWorkItemRelations(props.projectId, {
    page: 1,
    pageSize: 100,
    workItemId,
  })
  relations.value = result.data
}

function allowedTargets(status: WorkItemStatus): WorkItemStatus[] {
  const targets: Record<WorkItemStatus, WorkItemStatus[]> = {
    TODO: ['IN_PROGRESS', 'DONE', 'CANCELED'],
    IN_PROGRESS: ['TODO', 'DONE', 'CANCELED'],
    DONE: ['IN_PROGRESS'],
    CANCELED: ['TODO'],
  }
  return targets[status]
}

function typeLabel(type: WorkItemType): string {
  return { TASK: '任务', MILESTONE: '里程碑' }[type]
}

function statusLabel(status: WorkItemStatus): string {
  return { TODO: '待处理', IN_PROGRESS: '进行中', DONE: '已完成', CANCELED: '已取消' }[status]
}

function statusTagType(status: WorkItemStatus): 'primary' | 'success' | 'warning' | 'info' {
  return {
    TODO: 'info' as const,
    IN_PROGRESS: 'primary' as const,
    DONE: 'success' as const,
    CANCELED: 'warning' as const,
  }[status]
}

function priorityLabel(priority: WorkItemPriority): string {
  return { LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' }[priority]
}

function relationTypeLabel(type: WorkItemRelationType): string {
  return { PARENT_CHILD: '父子', PRECEDES: '前置', BLOCKS: '阻塞' }[type]
}

function relationDescription(relation: WorkItemRelation): string {
  const connector = {
    PARENT_CHILD: '包含',
    PRECEDES: '前置于',
    BLOCKS: '阻塞',
  }[relation.type]
  return `${relation.source.title} ${connector} ${relation.target.title}`
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="subpanel work-item-panel" aria-labelledby="work-item-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">计划与执行</p>
        <h2 id="work-item-title">统一工作项</h2>
        <p>统一跟踪任务、里程碑、状态历史和依赖关系。</p>
      </div>
      <ElButton v-if="canCreate" data-test="create-work-item" type="primary" @click="openCreate">
        新建工作项
      </ElButton>
    </div>

    <nav class="work-item-view-switcher" aria-label="工作项视图">
      <ElButton :type="viewMode === 'LIST' ? 'primary' : undefined" @click="viewMode = 'LIST'">列表</ElButton>
      <ElButton :type="viewMode === 'BOARD' ? 'primary' : undefined" @click="viewMode = 'BOARD'">看板</ElButton>
      <ElButton :type="viewMode === 'CALENDAR' ? 'primary' : undefined" @click="viewMode = 'CALENDAR'">日历</ElButton>
    </nav>

    <form v-if="viewMode === 'LIST'" class="work-item-filter-grid" aria-label="工作项筛选" @submit.prevent="loadItems(1)">
      <ElInput v-model="filters.keyword" clearable placeholder="搜索标题或描述" aria-label="搜索工作项" />
      <ElSelect v-model="filters.type" aria-label="工作项类型">
        <ElOption label="全部类型" value="" />
        <ElOption label="任务" value="TASK" />
        <ElOption label="里程碑" value="MILESTONE" />
      </ElSelect>
      <ElSelect v-model="filters.status" aria-label="工作项状态">
        <ElOption label="全部状态" value="" />
        <ElOption label="待处理" value="TODO" />
        <ElOption label="进行中" value="IN_PROGRESS" />
        <ElOption label="已完成" value="DONE" />
        <ElOption label="已取消" value="CANCELED" />
      </ElSelect>
      <ElSelect v-model="filters.priority" aria-label="工作项优先级">
        <ElOption label="全部优先级" value="" />
        <ElOption label="低" value="LOW" />
        <ElOption label="普通" value="NORMAL" />
        <ElOption label="高" value="HIGH" />
        <ElOption label="紧急" value="URGENT" />
      </ElSelect>
      <ElSelect v-model="filters.assigneeId" clearable placeholder="全部负责人" aria-label="工作项负责人">
        <ElOption v-for="memberItem in assignableMembers" :key="memberItem.userId" :label="memberItem.displayName" :value="memberItem.userId" />
      </ElSelect>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">
      {{ error }} <button type="button" @click="loadItems()">重试</button>
    </p>

    <div v-if="viewMode === 'LIST'" class="table-scroll" :aria-busy="loading">
      <ElTable
        :data="items"
        empty-text="暂无工作项"
        highlight-current-row
        @current-change="selectWorkItem($event as WorkItem | null)"
      >
        <ElTableColumn prop="title" label="工作项" min-width="240">
          <template #default="scope">
            <strong>{{ scope.row.title }}</strong>
            <span class="cell-secondary">{{ typeLabel(scope.row.type) }} #{{ scope.row.id }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="110">
          <template #default="scope"><ElTag :type="statusTagType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn prop="priority" label="优先级" width="90">
          <template #default="scope">{{ priorityLabel(scope.row.priority) }}</template>
        </ElTableColumn>
        <ElTableColumn label="负责人" min-width="120">
          <template #default="scope">{{ scope.row.assignee?.displayName || '未指派' }}</template>
        </ElTableColumn>
        <ElTableColumn label="计划周期" min-width="190">
          <template #default="scope">{{ scope.row.plannedStartDate || '未设置' }} — {{ scope.row.plannedEndDate || '未设置' }}</template>
        </ElTableColumn>
        <template #empty><ElEmpty description="没有符合条件的工作项" :image-size="72" /></template>
      </ElTable>
    </div>

    <ElPagination
      v-if="viewMode === 'LIST' && pagination.totalPages > 1"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.totalItems"
      @current-change="loadItems"
    />

    <WorkItemBoard
      v-if="viewMode === 'BOARD'"
      :project-id="projectId"
      :can-transition="canWrite"
      :current-user-id="currentUserId"
      :can-manage="canManage"
      :refresh-key="visualRefreshKey"
      @select="selectFromVisual"
      @transition="transitionFromBoard"
    />
    <WorkItemCalendar
      v-if="viewMode === 'CALENDAR'"
      :project-id="projectId"
      :refresh-key="visualRefreshKey"
      @select="selectFromVisual"
    />

    <section v-if="selectedItem" class="work-item-detail" :aria-busy="detailLoading" aria-labelledby="work-item-detail-title">
      <div class="work-item-detail-heading">
        <div>
          <p class="section-label">{{ typeLabel(selectedItem.type) }} #{{ selectedItem.id }}</p>
          <h3 id="work-item-detail-title">{{ selectedItem.title }}</h3>
        </div>
        <div class="detail-actions">
          <ElButton v-if="canEditSelected" data-test="edit-work-item" @click="openEdit">编辑</ElButton>
          <ElButton v-if="canEditSelected" data-test="transition-work-item" type="primary" plain @click="openTransition">流转状态</ElButton>
          <ElPopconfirm
            v-if="canDeleteSelected"
            title="确认删除该工作项？"
            confirm-button-text="确认删除"
            cancel-button-text="取消"
            @confirm="removeSelected"
          >
            <template #reference><ElButton data-test="delete-work-item" type="danger" plain>删除</ElButton></template>
          </ElPopconfirm>
        </div>
      </div>

      <p v-if="detailError" class="content-error" role="alert">{{ detailError }}</p>
      <div class="work-item-summary-grid">
        <div><span>状态</span><strong>{{ statusLabel(selectedItem.status) }}</strong></div>
        <div><span>优先级</span><strong>{{ priorityLabel(selectedItem.priority) }}</strong></div>
        <div><span>负责人</span><strong>{{ selectedItem.assignee?.displayName || '未指派' }}</strong></div>
        <div><span>计划周期</span><strong>{{ selectedItem.plannedStartDate || '未设置' }} — {{ selectedItem.plannedEndDate || '未设置' }}</strong></div>
        <div><span>实际周期</span><strong>{{ selectedItem.actualStartDate || '未开始' }} — {{ selectedItem.actualEndDate || '未完成' }}</strong></div>
      </div>
      <p class="work-item-description">{{ selectedItem.description || '暂无详细说明。' }}</p>

      <div class="work-item-trace-grid">
        <section aria-labelledby="status-history-title">
          <h4 id="status-history-title">状态历史</h4>
          <ol v-if="history.length" class="status-history-list">
            <li v-for="entry in history" :key="entry.id">
              <span class="history-dot" aria-hidden="true" />
              <div>
                <strong>{{ entry.fromStatus ? statusLabel(entry.fromStatus) : '已创建' }} → {{ statusLabel(entry.toStatus) }}</strong>
                <p v-if="entry.comment">{{ entry.comment }}</p>
                <small>{{ entry.changedBy.displayName }} · {{ formatDateTime(entry.changedAt) }}</small>
              </div>
            </li>
          </ol>
          <ElEmpty v-else description="暂无状态历史" :image-size="54" />
        </section>

        <section aria-labelledby="relations-title">
          <div class="trace-section-heading">
            <h4 id="relations-title">工作项关系</h4>
            <ElButton v-if="canManageRelations" data-test="create-work-item-relation" size="small" @click="openRelationEditor">添加关系</ElButton>
          </div>
          <ul v-if="relations.length" class="relation-list">
            <li v-for="relation in relations" :key="relation.id">
              <div><ElTag size="small" effect="plain">{{ relationTypeLabel(relation.type) }}</ElTag><span>{{ relationDescription(relation) }}</span></div>
              <ElPopconfirm
                v-if="canManageRelations"
                title="确认移除该关系？"
                confirm-button-text="移除"
                cancel-button-text="取消"
                @confirm="removeRelation(relation.id)"
              >
                <template #reference><ElButton link type="danger">移除</ElButton></template>
              </ElPopconfirm>
            </li>
          </ul>
          <ElEmpty v-else description="暂无工作项关系" :image-size="54" />
        </section>
      </div>
    </section>

    <WorkItemReminderPanel
      :project-id="projectId"
      :can-write="canWrite"
      :selected-work-item-id="selectedItem?.id"
    />

    <ElDialog v-model="editorOpen" :title="editing ? '编辑工作项' : '新建工作项'" width="min(42rem, 94vw)" destroy-on-close>
      <form class="work-item-editor-grid" @submit.prevent="saveEditor">
        <label><span>类型</span><ElSelect v-model="editorForm.type" :disabled="editing && !canManage"><ElOption label="任务" value="TASK" /><ElOption label="里程碑" value="MILESTONE" /></ElSelect></label>
        <label><span>优先级</span><ElSelect v-model="editorForm.priority"><ElOption label="低" value="LOW" /><ElOption label="普通" value="NORMAL" /><ElOption label="高" value="HIGH" /><ElOption label="紧急" value="URGENT" /></ElSelect></label>
        <label class="full-span"><span>标题</span><ElInput v-model="editorForm.title" maxlength="200" show-word-limit /></label>
        <label class="full-span"><span>详细说明</span><ElInput v-model="editorForm.description" type="textarea" :rows="4" maxlength="10000" show-word-limit /></label>
        <label><span>负责人</span><ElSelect v-model="editorForm.assigneeId" clearable placeholder="暂不指派" :disabled="editing && !canManage"><ElOption v-for="memberItem in assignableMembers" :key="memberItem.userId" :label="memberItem.displayName" :value="memberItem.userId" /></ElSelect></label>
        <label><span>计划开始</span><ElDatePicker v-model="editorForm.plannedStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" /></label>
        <label><span>计划结束</span><ElDatePicker v-model="editorForm.plannedEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" /></label>
        <p v-if="editorForm.plannedStartDate && editorForm.plannedEndDate && editorForm.plannedEndDate < editorForm.plannedStartDate" class="field-error full-span">计划结束日期不能早于开始日期。</p>
        <p v-if="editorError" class="content-error full-span" role="alert">{{ editorError }}</p>
      </form>
      <template #footer><ElButton @click="editorOpen = false">取消</ElButton><ElButton type="primary" :disabled="!editorReady" :loading="saving" @click="saveEditor">保存</ElButton></template>
    </ElDialog>

    <ElDialog v-model="transitionOpen" title="流转状态" width="min(30rem, 94vw)" destroy-on-close>
      <div class="dialog-form-stack">
        <label><span>目标状态</span><ElSelect v-model="transitionForm.status"><ElOption v-for="target in transitionTargets" :key="target" :label="statusLabel(target)" :value="target" /></ElSelect></label>
        <label><span>变更备注</span><ElInput v-model="transitionForm.comment" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明本次状态变更（可选）" /></label>
        <p v-if="transitionError" class="content-error" role="alert">{{ transitionError }}</p>
      </div>
      <template #footer><ElButton @click="transitionOpen = false">取消</ElButton><ElButton type="primary" :disabled="!transitionForm.status" :loading="transitioning" @click="saveTransition">确认流转</ElButton></template>
    </ElDialog>

    <ElDialog v-model="relationOpen" title="添加工作项关系" width="min(32rem, 94vw)" destroy-on-close>
      <div class="dialog-form-stack">
        <label><span>关系类型</span><ElSelect v-model="relationForm.type"><ElOption label="父子" value="PARENT_CHILD" /><ElOption label="前置" value="PRECEDES" /><ElOption label="阻塞" value="BLOCKS" /></ElSelect></label>
        <label><span>方向</span><ElSelect v-model="relationForm.direction"><ElOption label="当前工作项 → 关联工作项" value="SOURCE" /><ElOption label="关联工作项 → 当前工作项" value="TARGET" /></ElSelect></label>
        <label><span>关联工作项</span><ElSelect v-model="relationForm.relatedWorkItemId" filterable placeholder="选择工作项"><ElOption v-for="candidate in relationCandidates" :key="candidate.id" :label="`${candidate.title} (#${candidate.id})`" :value="candidate.id" /></ElSelect></label>
        <p class="form-hint">{{ relationForm.type === 'PARENT_CHILD' ? '箭头起点是父项，终点是子项。' : '箭头起点是前置/阻塞项，终点是后续/被阻塞项。' }}</p>
        <p v-if="relationError" class="content-error" role="alert">{{ relationError }}</p>
      </div>
      <template #footer><ElButton @click="relationOpen = false">取消</ElButton><ElButton type="primary" :disabled="!relationForm.relatedWorkItemId" :loading="relationSaving" @click="saveRelation">添加</ElButton></template>
    </ElDialog>
  </section>
</template>
