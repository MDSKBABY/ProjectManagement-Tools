<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
import { createProject, deleteProject, listProjects, updateProject } from '../api/projects'
import type {
  CurrentUser,
  Pagination,
  Project,
  ProjectCreateInput,
  ProjectStatus,
  ProjectUpdateInput,
} from '../types'
import DeploymentAssetPanel from './DeploymentAssetPanel.vue'
import DeploymentRecordPanel from './DeploymentRecordPanel.vue'
import DeploymentSolutionPanel from './DeploymentSolutionPanel.vue'
import DailyReportPanel from './DailyReportPanel.vue'
import DeliveryRecordsPanel from './DeliveryRecordsPanel.vue'
import ProjectLifecyclePanel from './ProjectLifecyclePanel.vue'
import EnvironmentFingerprintPanel from './EnvironmentFingerprintPanel.vue'
import KnowledgeArticlePanel from './KnowledgeArticlePanel.vue'
import ProjectEditorDialog from './ProjectEditorDialog.vue'
import ProjectFilePanel from './ProjectFilePanel.vue'
import ProjectMemberManagement from './ProjectMemberManagement.vue'
import ServerInventoryPanel from './ServerInventoryPanel.vue'
import WorkItemPanel from './WorkItemPanel.vue'
import WeeklyReportPanel from './WeeklyReportPanel.vue'

const props = defineProps<{ currentUser: CurrentUser }>()

type ProjectModule =
  | 'overview'
  | 'members'
  | 'files'
  | 'assets'
  | 'servers'
  | 'fingerprints'
  | 'solutions'
  | 'records'
  | 'work-items'
  | 'knowledge'
  | 'daily-reports'
  | 'weekly-reports'
  | 'vendors'
  | 'meetings'
  | 'design-assets'
  | 'lifecycle'

type ProjectModuleGroup = 'collaboration' | 'process' | 'delivery'
type ProjectModuleItem = { key: ProjectModule; label: string; group: ProjectModuleGroup }

const projects = ref<Project[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const keyword = ref('')
const status = ref<'' | ProjectStatus>('')
const loading = ref(false)
const error = ref('')
const selectedProject = ref<Project | null>(null)
const activeProjectModule = ref<ProjectModule>('overview')
const editorOpen = ref(false)
const editingProject = ref<Project | null>(null)
const saving = ref(false)
const editorError = ref('')
const deleting = ref(false)

const canCreate = computed(() => props.currentUser.permissions.includes('project:create'))
const canUpdate = computed(() => props.currentUser.permissions.includes('project:update'))
const canDelete = computed(() => props.currentUser.permissions.includes('project:delete'))
const canManageMembers = computed(() =>
  props.currentUser.permissions.includes('project:manage_members'),
)
const canReadFiles = computed(() => props.currentUser.permissions.includes('file:read'))
const canWriteFiles = computed(() => props.currentUser.permissions.includes('file:write'))
const canReadDeploymentAssets = computed(() =>
  props.currentUser.permissions.includes('deployment_asset:read'),
)
const canWriteDeploymentAssets = computed(() =>
  props.currentUser.permissions.includes('deployment_asset:write'),
)
const canReadServers = computed(() => props.currentUser.permissions.includes('server:read'))
const canWriteServers = computed(() => props.currentUser.permissions.includes('server:write'))
const canReadEnvironmentFingerprints = computed(() =>
  props.currentUser.permissions.includes('environment_fingerprint:read'),
)
const canWriteEnvironmentFingerprints = computed(() =>
  props.currentUser.permissions.includes('environment_fingerprint:write'),
)
const canReadDeploymentSolutions = computed(() =>
  props.currentUser.permissions.includes('deployment_solution:read'),
)
const canWriteDeploymentSolutions = computed(() =>
  props.currentUser.permissions.includes('deployment_solution:write'),
)
const canReadDeploymentRecords = computed(() =>
  props.currentUser.permissions.includes('deployment_record:read'),
)
const canWriteDeploymentRecords = computed(() =>
  props.currentUser.permissions.includes('deployment_record:write'),
)
const canReadWorkItems = computed(() => props.currentUser.permissions.includes('work_item:read'))
const canWriteWorkItems = computed(() => props.currentUser.permissions.includes('work_item:write'))
const canDeleteWorkItems = computed(() => props.currentUser.permissions.includes('work_item:delete'))
const canReadKnowledge = computed(() =>
  props.currentUser.permissions.includes('knowledge_article:read'),
)
const canWriteKnowledge = computed(() =>
  props.currentUser.permissions.includes('knowledge_article:write'),
)
const canReviewKnowledge = computed(() =>
  props.currentUser.permissions.includes('knowledge_article:review'),
)
const canReadDailyReports = computed(() =>
  props.currentUser.permissions.includes('daily_report:read'),
)
const canWriteDailyReports = computed(() =>
  props.currentUser.permissions.includes('daily_report:write'),
)
const canConfirmDailyReports = computed(() =>
  props.currentUser.permissions.includes('daily_report:confirm'),
)
const canReadWeeklyReports = computed(() => props.currentUser.permissions.includes('weekly_report:read'))
const canWriteWeeklyReports = computed(() => props.currentUser.permissions.includes('weekly_report:write'))
const canConfirmWeeklyReports = computed(() => props.currentUser.permissions.includes('weekly_report:confirm'))
const canReadVendors = computed(() => props.currentUser.permissions.includes('vendor_record:read'))
const canWriteVendors = computed(() => props.currentUser.permissions.includes('vendor_record:write'))
const canReadMeetings = computed(() => props.currentUser.permissions.includes('meeting_record:read'))
const canWriteMeetings = computed(() => props.currentUser.permissions.includes('meeting_record:write'))
const canReadDesigns = computed(() => props.currentUser.permissions.includes('design_asset:read'))
const canWriteDesigns = computed(() => props.currentUser.permissions.includes('design_asset:write'))
const canReadLifecycle = computed(() => props.currentUser.permissions.includes('project_lifecycle:read'))
const isSelectedProjectOwnerOrAdmin = computed(() =>
  props.currentUser.roles.includes('ADMIN') ||
  selectedProject.value?.owner.id === props.currentUser.id,
)
const canViewServerCredential = computed(() =>
  isSelectedProjectOwnerOrAdmin.value &&
  props.currentUser.permissions.includes('server_credential:read'),
)
const canManageServerCredential = computed(() =>
  isSelectedProjectOwnerOrAdmin.value &&
  props.currentUser.permissions.includes('server_credential:manage'),
)
const projectModuleGroupLabels: Record<ProjectModuleGroup, string> = {
  collaboration: '项目协作',
  process: '过程沉淀',
  delivery: '交付部署',
}

const projectModules = computed<ProjectModuleItem[]>(() => {
  const modules: ProjectModuleItem[] = [
    { key: 'overview', label: '概览', group: 'collaboration' },
    { key: 'members', label: '成员', group: 'collaboration' },
  ]
  if (canReadWorkItems.value) modules.push({ key: 'work-items', label: '工作项', group: 'collaboration' })
  if (canReadLifecycle.value) modules.push({ key: 'lifecycle', label: '全周期', group: 'collaboration' })
  if (canReadKnowledge.value) modules.push({ key: 'knowledge', label: '技术知识', group: 'process' })
  if (canReadDailyReports.value) modules.push({ key: 'daily-reports', label: '日报', group: 'process' })
  if (canReadWeeklyReports.value) modules.push({ key: 'weekly-reports', label: '周报', group: 'process' })
  if (canReadMeetings.value) modules.push({ key: 'meetings', label: '会议培训', group: 'process' })
  if (canReadDesigns.value) modules.push({ key: 'design-assets', label: '设计资料', group: 'process' })
  if (canReadFiles.value) modules.push({ key: 'files', label: '文件', group: 'process' })
  if (canReadVendors.value) modules.push({ key: 'vendors', label: '厂商接口', group: 'delivery' })
  if (canReadDeploymentAssets.value) modules.push({ key: 'assets', label: '部署资产', group: 'delivery' })
  if (canReadServers.value) modules.push({ key: 'servers', label: '服务器', group: 'delivery' })
  if (canReadEnvironmentFingerprints.value) modules.push({ key: 'fingerprints', label: '环境指纹', group: 'delivery' })
  if (canReadDeploymentSolutions.value) modules.push({ key: 'solutions', label: '部署方案', group: 'delivery' })
  if (canReadDeploymentRecords.value) modules.push({ key: 'records', label: '部署记录', group: 'delivery' })
  return modules
})
const projectModuleGroups = computed(() => {
  return (Object.keys(projectModuleGroupLabels) as ProjectModuleGroup[])
    .map((key) => ({
      key,
      label: projectModuleGroupLabels[key],
      modules: projectModules.value.filter((module) => module.group === key),
    }))
    .filter((group) => group.modules.length > 0)
})

onMounted(loadProjects)

async function loadProjects(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listProjects({
      page,
      pageSize: pagination.pageSize,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    })
    projects.value = result.data
    Object.assign(pagination, result.pagination)
    if (selectedProject.value) {
      selectedProject.value = result.data.find(({ id }) => id === selectedProject.value?.id) ?? null
    }
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function selectProject(project: Project | null): void {
  selectedProject.value = project
  activeProjectModule.value = 'overview'
}

function returnToProjectList(): void {
  selectProject(null)
}

function openCreate(): void {
  editingProject.value = null
  editorError.value = ''
  editorOpen.value = true
}

function openEdit(project: Project): void {
  editingProject.value = project
  editorError.value = ''
  editorOpen.value = true
}

async function saveProject(input: ProjectCreateInput | ProjectUpdateInput): Promise<void> {
  saving.value = true
  editorError.value = ''
  try {
    if (editingProject.value) {
      selectedProject.value = await updateProject(editingProject.value.id, input as ProjectUpdateInput)
      await loadProjects()
    } else {
      const created = await createProject(input as ProjectCreateInput)
      editorOpen.value = false
      await loadProjects(1)
      selectedProject.value = created
    }
    editorOpen.value = false
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function removeSelectedProject(): Promise<void> {
  if (!selectedProject.value) return
  deleting.value = true
  error.value = ''
  try {
    await deleteProject(selectedProject.value.id)
    selectedProject.value = null
    await loadProjects(1)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    deleting.value = false
  }
}

function statusLabel(projectStatus: ProjectStatus): string {
  return {
    PLANNING: '规划中',
    ACTIVE: '进行中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    ARCHIVED: '已归档',
  }[projectStatus]
}

function statusType(projectStatus: ProjectStatus): 'primary' | 'success' | 'warning' | 'info' {
  return {
    PLANNING: 'primary' as const,
    ACTIVE: 'success' as const,
    PAUSED: 'warning' as const,
    COMPLETED: 'info' as const,
    ARCHIVED: 'info' as const,
  }[projectStatus]
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="panel project-workspace-panel" aria-labelledby="projects-title">
    <div v-if="!selectedProject" class="project-list-view" data-test="project-list-view">
      <div class="panel-heading">
      <div>
        <p class="section-label">项目空间</p>
        <h1 id="projects-title">项目工作台</h1>
        <p>集中查看项目状态、负责人和计划周期。</p>
      </div>
      <ElButton v-if="canCreate" data-test="create-project" type="primary" @click="openCreate">
        创建项目
      </ElButton>
    </div>

    <form class="filter-bar" aria-label="项目筛选" @submit.prevent="loadProjects(1)">
      <ElInput
        v-model="keyword"
        clearable
        placeholder="搜索项目编码、名称或客户"
        aria-label="搜索项目"
      />
      <ElSelect v-model="status" aria-label="项目状态">
        <ElOption label="全部状态" value="" />
        <ElOption label="规划中" value="PLANNING" />
        <ElOption label="进行中" value="ACTIVE" />
        <ElOption label="已暂停" value="PAUSED" />
        <ElOption label="已完成" value="COMPLETED" />
        <ElOption label="已归档" value="ARCHIVED" />
      </ElSelect>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">
      {{ error }}
      <button type="button" @click="loadProjects()">重试</button>
    </p>

    <div class="table-scroll" :aria-busy="loading">
      <ElTable
        :data="projects"
        empty-text="暂无项目"
        highlight-current-row
        @current-change="selectProject($event as Project | null)"
      >
        <ElTableColumn prop="name" label="项目" min-width="220">
          <template #default="scope">
            <strong>{{ scope.row.name }}</strong>
            <span class="cell-secondary">{{ scope.row.code }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="customerName" label="客户" min-width="160">
          <template #default="scope">{{ scope.row.customerName || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="owner.displayName" label="负责人" min-width="120" />
        <ElTableColumn prop="status" label="状态" width="110">
          <template #default="scope">
            <ElTag :type="statusType(scope.row.status)" effect="plain">
              {{ statusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="计划周期" min-width="190">
          <template #default="scope">
            {{ scope.row.startDate || '未设置' }} — {{ scope.row.endDate || '未设置' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="96" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              :data-test="`enter-project-${scope.row.id}`"
              @click.stop="selectProject(scope.row as Project)"
            >
              进入项目
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="没有符合条件的项目" :image-size="72" />
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
      @current-change="loadProjects"
    />
    </div>

    <div v-else class="project-workspace-view">
      <header class="project-workspace-header">
        <div>
          <button
            type="button"
            class="back-to-projects"
            data-test="back-to-projects"
            @click="returnToProjectList"
          >
            ← 返回项目列表
          </button>
          <p class="project-breadcrumb">项目工作台 / {{ selectedProject.name }}</p>
          <h1 id="projects-title">{{ selectedProject.name }}</h1>
        </div>
        <div class="detail-actions">
          <ElButton v-if="canUpdate" @click="openEdit(selectedProject)">编辑项目</ElButton>
          <ElPopconfirm
            v-if="canDelete"
            title="确认删除该项目？项目将被归档隐藏。"
            confirm-button-text="确认删除"
            cancel-button-text="取消"
            @confirm="removeSelectedProject"
          >
            <template #reference>
              <ElButton type="danger" plain :loading="deleting">删除项目</ElButton>
            </template>
          </ElPopconfirm>
        </div>
      </header>

      <div class="project-workspace-layout">
        <aside class="project-context-sidebar">
          <div class="project-context-card">
            <span>当前项目</span>
            <strong>{{ selectedProject.name }}</strong>
            <small>{{ selectedProject.code }}</small>
          </div>
          <nav :aria-label="`${selectedProject.name}项目模块`">
            <section v-for="group in projectModuleGroups" :key="group.key" class="project-menu-group">
              <h2>{{ group.label }}</h2>
              <button
                v-for="module in group.modules"
                :key="module.key"
                type="button"
                :data-test="`project-module-${module.key}`"
                :class="{ active: activeProjectModule === module.key }"
                :aria-current="activeProjectModule === module.key ? 'page' : undefined"
                @click="activeProjectModule = module.key"
              >
                {{ module.label }}
              </button>
            </section>
          </nav>
        </aside>

        <main class="project-module-content">

    <section
      v-if="selectedProject && activeProjectModule === 'overview'"
      class="project-detail"
      aria-labelledby="project-detail-title"
    >
      <div>
        <p class="section-label">当前项目</p>
        <h2 id="project-detail-title">{{ selectedProject.name }}</h2>
        <p>{{ selectedProject.description || '暂无项目说明。' }}</p>
        <dl class="detail-grid">
          <div><dt>项目编码</dt><dd>{{ selectedProject.code }}</dd></div>
          <div><dt>负责人</dt><dd>{{ selectedProject.owner.displayName }}</dd></div>
          <div><dt>客户</dt><dd>{{ selectedProject.customerName || '未设置' }}</dd></div>
          <div><dt>标签</dt><dd>{{ selectedProject.tags.join('、') || '未设置' }}</dd></div>
        </dl>
      </div>
    </section>

    <ProjectMemberManagement
      v-if="selectedProject && activeProjectModule === 'members'"
      :project-id="selectedProject.id"
      :can-manage="canManageMembers"
    />

    <WorkItemPanel
      v-if="selectedProject && activeProjectModule === 'work-items' && canReadWorkItems"
      :project-id="selectedProject.id"
      :current-user-id="currentUser.id"
      :is-administrator="currentUser.roles.includes('ADMIN')"
      :can-write="canWriteWorkItems"
      :can-delete="canDeleteWorkItems"
    />

    <KnowledgeArticlePanel
      v-if="selectedProject && activeProjectModule === 'knowledge' && canReadKnowledge"
      :project-id="selectedProject.id"
      :current-user-id="currentUser.id"
      :can-write="canWriteKnowledge"
      :can-review="canReviewKnowledge"
    />

    <DailyReportPanel
      v-if="selectedProject && activeProjectModule === 'daily-reports' && canReadDailyReports"
      :project-id="selectedProject.id"
      :current-user-id="currentUser.id"
      :can-write="canWriteDailyReports"
      :can-confirm="canConfirmDailyReports"
    />

    <WeeklyReportPanel
      v-if="selectedProject && activeProjectModule === 'weekly-reports' && canReadWeeklyReports"
      :project-id="selectedProject.id"
      :can-write="canWriteWeeklyReports"
      :can-confirm="canConfirmWeeklyReports"
    />

    <DeliveryRecordsPanel
      v-if="selectedProject && activeProjectModule === 'vendors' && canReadVendors"
      :project-id="selectedProject.id" mode="vendor" :can-write="canWriteVendors"
    />
    <DeliveryRecordsPanel
      v-if="selectedProject && activeProjectModule === 'meetings' && canReadMeetings"
      :project-id="selectedProject.id" mode="meeting" :can-write="canWriteMeetings"
    />
    <DeliveryRecordsPanel
      v-if="selectedProject && activeProjectModule === 'design-assets' && canReadDesigns"
      :project-id="selectedProject.id" mode="design" :can-write="canWriteDesigns"
    />
    <ProjectLifecyclePanel
      v-if="selectedProject && activeProjectModule === 'lifecycle' && canReadLifecycle"
      :project-id="selectedProject.id"
    />

    <ProjectFilePanel
      v-if="selectedProject && activeProjectModule === 'files' && canReadFiles"
      :project-id="selectedProject.id"
      :can-write="canWriteFiles"
    />

    <DeploymentAssetPanel
      v-if="selectedProject && activeProjectModule === 'assets' && canReadDeploymentAssets"
      :project-id="selectedProject.id"
      :can-write="canWriteDeploymentAssets"
    />

    <ServerInventoryPanel
      v-if="selectedProject && activeProjectModule === 'servers' && canReadServers"
      :project-id="selectedProject.id"
      :can-write="canWriteServers"
      :can-view-credential="canViewServerCredential"
      :can-manage-credential="canManageServerCredential"
    />

    <EnvironmentFingerprintPanel
      v-if="selectedProject && activeProjectModule === 'fingerprints' && canReadEnvironmentFingerprints"
      :project-id="selectedProject.id"
      :can-write="canWriteEnvironmentFingerprints"
    />

    <DeploymentSolutionPanel
      v-if="selectedProject && activeProjectModule === 'solutions' && canReadDeploymentSolutions"
      :project-id="selectedProject.id"
      :can-write="canWriteDeploymentSolutions"
    />

    <DeploymentRecordPanel
      v-if="selectedProject && activeProjectModule === 'records' && canReadDeploymentRecords"
      :project-id="selectedProject.id"
      :can-write="canWriteDeploymentRecords"
    />
        </main>
      </div>
    </div>

    <ProjectEditorDialog
      :open="editorOpen"
      :saving="saving"
      :error="editorError"
      :project="editingProject"
      @close="editorOpen = false"
      @submit="saveProject"
    />
  </section>
</template>
