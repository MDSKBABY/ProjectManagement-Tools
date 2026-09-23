<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
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
import {
  createServer,
  deleteServer,
  listServers,
  saveServerCredential,
  updateServer,
  viewServerCredential,
} from '../api/servers'
import type {
  DeploymentEnvironment,
  Pagination,
  SaveServerInput,
  ServerCredential,
  ServerRecord,
  ServerStatus,
} from '../types'

const props = defineProps<{
  projectId: number
  canWrite: boolean
  canViewCredential: boolean
  canManageCredential: boolean
}>()

const servers = ref<ServerRecord[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({
  keyword: '',
  environment: '' as '' | DeploymentEnvironment,
  status: '' as '' | ServerStatus,
})
const loading = ref(false)
const error = ref('')
const editorOpen = ref(false)
const editingServer = ref<ServerRecord | null>(null)
const saving = ref(false)
const editorError = ref('')
const credentialEditorOpen = ref(false)
const credentialTarget = ref<ServerRecord | null>(null)
const credentialSaving = ref(false)
const credentialError = ref('')
const credentialViewerOpen = ref(false)
const credentialLoading = ref(false)
const revealedCredential = ref<ServerCredential | null>(null)

const emptyForm = (): SaveServerInput => ({
  name: '',
  host: '',
  port: 22,
  environment: 'GENERAL',
  status: 'ACTIVE',
  operatingSystem: '',
  architecture: '',
  purpose: '',
  description: '',
})
const form = reactive<SaveServerInput>(emptyForm())
const credentialForm = reactive({ username: '', password: '' })

const formReady = computed(() =>
  Boolean(
    form.name.trim() &&
      /^[A-Za-z0-9._:-]+$/.test(form.host.trim()) &&
      form.port >= 1 &&
      form.port <= 65535,
  ),
)
const credentialReady = computed(() =>
  Boolean(credentialForm.username.trim() && credentialForm.password),
)

onMounted(() => loadServers())
watch(() => props.projectId, () => {
  clearRevealedCredential()
  loadServers(1)
})

async function loadServers(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listServers(props.projectId, {
      page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword.trim() || undefined,
      environment: filters.environment || undefined,
      status: filters.status || undefined,
    })
    servers.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openEditor(server?: ServerRecord): void {
  editingServer.value = server ?? null
  editorError.value = ''
  Object.assign(form, emptyForm())
  if (server) {
    Object.assign(form, {
      name: server.name,
      host: server.host,
      port: server.port,
      environment: server.environment,
      status: server.status,
      operatingSystem: server.operatingSystem ?? '',
      architecture: server.architecture ?? '',
      purpose: server.purpose ?? '',
      description: server.description ?? '',
    })
  }
  editorOpen.value = true
}

async function save(): Promise<void> {
  if (!formReady.value) return
  saving.value = true
  editorError.value = ''
  const input: SaveServerInput = {
    ...form,
    name: form.name.trim(),
    host: form.host.trim(),
    operatingSystem: form.operatingSystem?.trim() || undefined,
    architecture: form.architecture?.trim() || undefined,
    purpose: form.purpose?.trim() || undefined,
    description: form.description?.trim() || undefined,
  }
  try {
    if (editingServer.value) {
      await updateServer(props.projectId, editingServer.value.id, input)
    } else {
      await createServer(props.projectId, input)
    }
    editorOpen.value = false
    await loadServers(editingServer.value ? pagination.page : 1)
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function remove(server: ServerRecord): Promise<void> {
  error.value = ''
  try {
    await deleteServer(props.projectId, server.id)
    await loadServers(1)
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

function openRowEditor(row: unknown): void {
  openEditor(row as ServerRecord)
}

function removeRow(row: unknown): Promise<void> {
  return remove(row as ServerRecord)
}

function openRowCredentialEditor(row: unknown): void {
  openCredentialEditor(row as ServerRecord)
}

function revealRowCredential(row: unknown): Promise<void> {
  return revealCredential(row as ServerRecord)
}

function openCredentialEditor(server: ServerRecord): void {
  credentialTarget.value = server
  credentialForm.username = ''
  credentialForm.password = ''
  credentialError.value = ''
  credentialEditorOpen.value = true
}

async function saveCredential(): Promise<void> {
  if (!credentialTarget.value || !credentialReady.value) return
  credentialSaving.value = true
  credentialError.value = ''
  try {
    await saveServerCredential(props.projectId, credentialTarget.value.id, {
      username: credentialForm.username.trim(),
      password: credentialForm.password,
    })
    clearCredentialEditor()
    await loadServers()
  } catch (reason) {
    credentialError.value = errorMessage(reason)
  } finally {
    credentialSaving.value = false
  }
}

/** 取消或保存后都立即丢弃尚未加密的用户名和密码。 */
function clearCredentialEditor(): void {
  credentialEditorOpen.value = false
  credentialForm.username = ''
  credentialForm.password = ''
  credentialTarget.value = null
  credentialError.value = ''
}

function handleCredentialEditorVisibility(open: boolean): void {
  if (!open) clearCredentialEditor()
}

async function revealCredential(server: ServerRecord): Promise<void> {
  clearRevealedCredential()
  credentialTarget.value = server
  credentialViewerOpen.value = true
  credentialLoading.value = true
  credentialError.value = ''
  try {
    revealedCredential.value = await viewServerCredential(props.projectId, server.id)
  } catch (reason) {
    credentialError.value = errorMessage(reason)
  } finally {
    credentialLoading.value = false
  }
}

/** 关闭查看窗口即丢弃明文引用，避免凭据继续留在组件状态。 */
function clearRevealedCredential(): void {
  credentialViewerOpen.value = false
  revealedCredential.value = null
  credentialTarget.value = null
  credentialError.value = ''
}

function handleCredentialViewerVisibility(open: boolean): void {
  if (!open) clearRevealedCredential()
}

function environmentLabel(environment: DeploymentEnvironment): string {
  return {
    DEVELOPMENT: '开发',
    TESTING: '测试',
    STAGING: '预发布',
    PRODUCTION: '生产',
    GENERAL: '通用',
  }[environment]
}

function statusLabel(status: ServerStatus): string {
  return { ACTIVE: '运行中', MAINTENANCE: '维护中', RETIRED: '已退役' }[status]
}

function statusType(status: ServerStatus): 'success' | 'warning' | 'info' {
  return { ACTIVE: 'success' as const, MAINTENANCE: 'warning' as const, RETIRED: 'info' as const }[status]
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="subpanel server-inventory" aria-labelledby="server-inventory-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">基础设施</p>
        <h2 id="server-inventory-title">服务器档案</h2>
      </div>
      <ElButton v-if="canWrite" data-test="create-server" type="primary" @click="openEditor()">
        新建服务器
      </ElButton>
    </div>

    <form class="server-filter-grid" aria-label="服务器筛选" @submit.prevent="loadServers(1)">
      <ElInput v-model="filters.keyword" clearable placeholder="名称、主机或用途" aria-label="搜索服务器" />
      <ElSelect v-model="filters.environment" aria-label="服务器环境">
        <ElOption label="全部环境" value="" />
        <ElOption label="开发" value="DEVELOPMENT" />
        <ElOption label="测试" value="TESTING" />
        <ElOption label="预发布" value="STAGING" />
        <ElOption label="生产" value="PRODUCTION" />
        <ElOption label="通用" value="GENERAL" />
      </ElSelect>
      <ElSelect v-model="filters.status" aria-label="服务器状态">
        <ElOption label="全部状态" value="" />
        <ElOption label="运行中" value="ACTIVE" />
        <ElOption label="维护中" value="MAINTENANCE" />
        <ElOption label="已退役" value="RETIRED" />
      </ElSelect>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="servers">
        <ElTableColumn label="服务器" min-width="200">
          <template #default="scope">
            <strong>{{ scope.row.name }}</strong>
            <span class="cell-secondary">{{ scope.row.host }}:{{ scope.row.port }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="环境" width="100">
          <template #default="scope">{{ environmentLabel(scope.row.environment) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="scope">
            <ElTag :type="statusType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="系统 / 架构" min-width="150">
          <template #default="scope">{{ scope.row.operatingSystem || '未设置' }} / {{ scope.row.architecture || '未设置' }}</template>
        </ElTableColumn>
        <ElTableColumn label="凭据" min-width="150">
          <template #default="scope">
            <span>{{ scope.row.credentialConfigured ? '已配置（默认隐藏）' : '未配置' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" min-width="250" fixed="right">
          <template #default="scope">
            <ElButton v-if="canWrite" link type="primary" @click="openRowEditor(scope.row)">编辑</ElButton>
            <ElButton
              v-if="canViewCredential && scope.row.credentialConfigured"
              data-test="view-server-credential"
              link
              @click="revealRowCredential(scope.row)"
            >查看凭据</ElButton>
            <ElButton
              v-if="canManageCredential"
              data-test="edit-server-credential"
              link
              @click="openRowCredentialEditor(scope.row)"
            >{{ scope.row.credentialConfigured ? '更新凭据' : '配置凭据' }}</ElButton>
            <ElPopconfirm
              v-if="canWrite"
              title="确认删除该服务器档案？"
              confirm-button-text="确认删除"
              cancel-button-text="取消"
              @confirm="removeRow(scope.row)"
            >
              <template #reference><ElButton link type="danger">删除</ElButton></template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无服务器档案" :image-size="64" /></template>
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
      @current-change="loadServers"
    />

    <ElDialog :model-value="editorOpen" :title="editingServer ? '编辑服务器' : '新建服务器'" width="min(720px, 94vw)" @close="editorOpen = false">
      <form class="server-editor-grid" aria-label="服务器档案表单" @submit.prevent="save">
        <label>服务器名称<ElInput v-model="form.name" maxlength="100" /></label>
        <label>主机名或 IP<ElInput v-model="form.host" maxlength="253" placeholder="例如 app-01.internal" /></label>
        <label>端口<ElInput v-model.number="form.port" type="number" min="1" max="65535" /></label>
        <label>环境
          <ElSelect v-model="form.environment"><ElOption label="开发" value="DEVELOPMENT" /><ElOption label="测试" value="TESTING" /><ElOption label="预发布" value="STAGING" /><ElOption label="生产" value="PRODUCTION" /><ElOption label="通用" value="GENERAL" /></ElSelect>
        </label>
        <label>状态
          <ElSelect v-model="form.status"><ElOption label="运行中" value="ACTIVE" /><ElOption label="维护中" value="MAINTENANCE" /><ElOption label="已退役" value="RETIRED" /></ElSelect>
        </label>
        <label>操作系统<ElInput v-model="form.operatingSystem" maxlength="100" placeholder="例如 Linux" /></label>
        <label>系统架构<ElInput v-model="form.architecture" maxlength="64" placeholder="例如 amd64" /></label>
        <label>用途<ElInput v-model="form.purpose" maxlength="200" /></label>
        <label class="server-editor-wide">说明<ElInput v-model="form.description" type="textarea" :rows="3" maxlength="5000" /></label>
        <p v-if="editorError" class="content-error server-editor-wide" role="alert">{{ editorError }}</p>
      </form>
      <template #footer>
        <ElButton @click="editorOpen = false">取消</ElButton>
        <ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存服务器</ElButton>
      </template>
    </ElDialog>

    <ElDialog
      :model-value="credentialEditorOpen"
      title="配置服务器凭据"
      width="min(520px, 94vw)"
      @update:model-value="handleCredentialEditorVisibility"
      @close="clearCredentialEditor"
    >
      <p class="dialog-help">凭据提交后仅以密文保存。更新操作会覆盖此前凭据并记录审计。</p>
      <form class="credential-form" aria-label="服务器凭据表单" @submit.prevent="saveCredential">
        <label>登录用户名<ElInput v-model="credentialForm.username" maxlength="200" autocomplete="off" /></label>
        <label>登录密码<ElInput v-model="credentialForm.password" type="password" maxlength="4096" show-password autocomplete="new-password" /></label>
        <p v-if="credentialError" class="content-error" role="alert">{{ credentialError }}</p>
      </form>
      <template #footer>
        <ElButton @click="clearCredentialEditor">取消</ElButton>
        <ElButton type="primary" :disabled="!credentialReady" :loading="credentialSaving" @click="saveCredential">加密保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog
      :model-value="credentialViewerOpen"
      title="查看服务器凭据"
      width="min(560px, 94vw)"
      @update:model-value="handleCredentialViewerVisibility"
      @close="clearRevealedCredential"
    >
      <p class="credential-warning">敏感信息：仅在必要时查看，请勿截图、转发或粘贴到不受控位置。</p>
      <p v-if="credentialLoading" role="status">正在安全读取凭据…</p>
      <p v-if="credentialError" class="content-error" role="alert">{{ credentialError }}</p>
      <dl v-if="revealedCredential" class="credential-details">
        <div><dt>用户名</dt><dd><code>{{ revealedCredential.username }}</code></dd></div>
        <div><dt>密码</dt><dd><code>{{ revealedCredential.password }}</code></dd></div>
      </dl>
      <template #footer><ElButton type="primary" @click="clearRevealedCredential">关闭并清除</ElButton></template>
    </ElDialog>
  </section>
</template>
