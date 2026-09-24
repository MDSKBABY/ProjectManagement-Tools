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
import EnvironmentFingerprintPanel from './EnvironmentFingerprintPanel.vue'
import ProjectEditorDialog from './ProjectEditorDialog.vue'
import ProjectFilePanel from './ProjectFilePanel.vue'
import ProjectMemberManagement from './ProjectMemberManagement.vue'
import ServerInventoryPanel from './ServerInventoryPanel.vue'
import WorkItemPanel from './WorkItemPanel.vue'

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
const projectModules = computed<Array<{ key: ProjectModule; label: string }>>(() => {
  const modules: Array<{ key: ProjectModule; label: string }> = [
    { key: 'overview', label: '概览' },
    { key: 'members', label: '成员' },
  ]
  if (canReadWorkItems.value) modules.push({ key: 'work-items', label: '工作项' })
  if (canReadFiles.value) modules.push({ key: 'files', label: '文件' })
  if (canReadDeploymentAssets.value) modules.push({ key: 'assets', label: '部署资产' })
  if (canReadServers.value) modules.push({ key: 'servers', label: '服务器' })
  if (canReadEnvironmentFingerprints.value) modules.push({ key: 'fingerprints', label: '环境指纹' })
  if (canReadDeploymentSolutions.value) modules.push({ key: 'solutions', label: '部署方案' })
  if (canReadDeploymentRecords.value) modules.push({ key: 'records', label: '部署记录' })
  return modules
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
  <section class="panel" aria-labelledby="projects-title">
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

    <nav v-if="selectedProject" class="project-module-nav" aria-label="项目模块导航">
      <button
        v-for="module in projectModules"
        :key="module.key"
        type="button"
        :data-test="`project-module-${module.key}`"
        :class="{ active: activeProjectModule === module.key }"
        @click="activeProjectModule = module.key"
      >
        {{ module.label }}
      </button>
    </nav>

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
