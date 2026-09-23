<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import {
  createDeploymentAsset,
  listDeploymentAssets,
  listDeploymentAssetVersions,
} from '../api/deployment-assets'
import { listProjectFiles } from '../api/files'
import type {
  CreateDeploymentAssetInput,
  DeploymentAsset,
  DeploymentAssetType,
  DeploymentEnvironment,
  FileAsset,
  Pagination,
  RiskLevel,
} from '../types'

const props = defineProps<{ projectId: number; canWrite: boolean }>()

const assets = ref<DeploymentAsset[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({
  keyword: '',
  assetType: '' as '' | DeploymentAssetType,
  operatingSystem: '',
  architecture: '',
  environment: '' as '' | DeploymentEnvironment,
  riskLevel: '' as '' | RiskLevel,
  tag: '',
})
const loading = ref(false)
const error = ref('')
const editorOpen = ref(false)
const saving = ref(false)
const editorError = ref('')
const availableFiles = ref<FileAsset[]>([])
const selectedAsset = ref<DeploymentAsset | null>(null)
const versionsOpen = ref(false)
const versions = ref<DeploymentAsset[]>([])

type DeploymentAssetForm = Omit<CreateDeploymentAssetInput, 'fileAssetId'> & {
  fileAssetId: number | null
}

const emptyForm = (): DeploymentAssetForm => ({
  name: '',
  assetType: 'INSTALLATION_PACKAGE',
  versionLabel: '',
  fileAssetId: null,
  operatingSystem: '',
  architecture: '',
  environment: 'GENERAL',
  riskLevel: 'LOW',
  tags: [],
  description: '',
  prerequisites: '',
  executionInstructions: '',
  rollbackInstructions: '',
})
const form = reactive<DeploymentAssetForm>(emptyForm())
const tagText = ref('')

const scriptSelected = computed(() => form.assetType === 'SCRIPT')
const formReady = computed(() => {
  const base = form.name.trim() && form.versionLabel.trim() && (form.fileAssetId ?? 0) > 0
  if (!base) return false
  return !scriptSelected.value || Boolean(
    form.prerequisites?.trim() &&
      form.executionInstructions?.trim() &&
      form.rollbackInstructions?.trim(),
  )
})

onMounted(() => loadAssets())
watch(() => props.projectId, () => {
  selectedAsset.value = null
  loadAssets(1)
})

async function loadAssets(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listDeploymentAssets(props.projectId, {
      page,
      pageSize: pagination.pageSize,
      keyword: filters.keyword.trim() || undefined,
      assetType: filters.assetType || undefined,
      operatingSystem: filters.operatingSystem.trim() || undefined,
      architecture: filters.architecture.trim() || undefined,
      environment: filters.environment || undefined,
      riskLevel: filters.riskLevel || undefined,
      tag: filters.tag.trim() || undefined,
    })
    assets.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function openEditor(source?: DeploymentAsset): Promise<void> {
  Object.assign(form, emptyForm())
  tagText.value = ''
  editorError.value = ''
  if (source) {
    Object.assign(form, {
      assetGroupId: source.assetGroupId,
      name: source.name,
      assetType: source.assetType,
      operatingSystem: source.operatingSystem ?? '',
      architecture: source.architecture ?? '',
      environment: source.environment,
      riskLevel: source.riskLevel,
      tags: [...source.tags],
      description: source.description ?? '',
      prerequisites: source.prerequisites ?? '',
      executionInstructions: source.executionInstructions ?? '',
      rollbackInstructions: source.rollbackInstructions ?? '',
      versionLabel: '',
      fileAssetId: null,
    })
    tagText.value = source.tags.join(', ')
  }
  editorOpen.value = true
  try {
    const result = await listProjectFiles(props.projectId, { page: 1, pageSize: 100 })
    availableFiles.value = result.data.filter(({ status }) => status === 'AVAILABLE')
  } catch (reason) {
    editorError.value = errorMessage(reason)
  }
}

async function save(): Promise<void> {
  if (!formReady.value) return
  saving.value = true
  editorError.value = ''
  try {
    form.tags = tagText.value
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag, index, all) => tag && all.indexOf(tag) === index)
      .slice(0, 10)
    const created = await createDeploymentAsset(props.projectId, {
      ...form,
      fileAssetId: form.fileAssetId as number,
      name: form.name.trim(),
      versionLabel: form.versionLabel.trim(),
    })
    editorOpen.value = false
    selectedAsset.value = created
    await loadAssets(1)
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function showVersions(asset: DeploymentAsset): Promise<void> {
  error.value = ''
  try {
    versions.value = await listDeploymentAssetVersions(props.projectId, asset.assetGroupId)
    versionsOpen.value = true
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

function selectAsset(row: unknown): void {
  selectedAsset.value = row as DeploymentAsset
}

function showRowVersions(row: unknown): Promise<void> {
  return showVersions(row as DeploymentAsset)
}

function openRowVersionEditor(row: unknown): Promise<void> {
  return openEditor(row as DeploymentAsset)
}

function typeLabel(type: DeploymentAssetType): string {
  return {
    INSTALLATION_PACKAGE: '安装包',
    SCRIPT: '脚本',
    MANUAL: '手册',
    CONFIG_TEMPLATE: '配置模板',
    DEPENDENCY: '依赖组件',
  }[type]
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

function riskLabel(risk: RiskLevel): string {
  return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }[risk]
}

function riskType(risk: RiskLevel): 'success' | 'warning' | 'danger' | 'info' {
  return { LOW: 'success' as const, MEDIUM: 'info' as const, HIGH: 'warning' as const, CRITICAL: 'danger' as const }[risk]
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="subpanel deployment-assets" aria-labelledby="deployment-assets-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">可复用交付物</p>
        <h2 id="deployment-assets-title">部署资产</h2>
      </div>
      <ElButton v-if="canWrite" data-test="create-deployment-asset" type="primary" @click="openEditor()">
        新建资产
      </ElButton>
    </div>

    <form class="asset-filter-grid" aria-label="部署资产筛选" @submit.prevent="loadAssets(1)">
      <ElInput v-model="filters.keyword" clearable placeholder="名称或版本" aria-label="搜索部署资产" />
      <ElSelect v-model="filters.assetType" aria-label="资产类型">
        <ElOption label="全部类型" value="" />
        <ElOption label="安装包" value="INSTALLATION_PACKAGE" />
        <ElOption label="脚本" value="SCRIPT" />
        <ElOption label="手册" value="MANUAL" />
        <ElOption label="配置模板" value="CONFIG_TEMPLATE" />
        <ElOption label="依赖组件" value="DEPENDENCY" />
      </ElSelect>
      <ElInput v-model="filters.operatingSystem" clearable placeholder="如 Linux" aria-label="操作系统" />
      <ElInput v-model="filters.architecture" clearable placeholder="如 amd64" aria-label="系统架构" />
      <ElSelect v-model="filters.environment" aria-label="目标环境">
        <ElOption label="全部环境" value="" />
        <ElOption label="开发" value="DEVELOPMENT" />
        <ElOption label="测试" value="TESTING" />
        <ElOption label="预发布" value="STAGING" />
        <ElOption label="生产" value="PRODUCTION" />
        <ElOption label="通用" value="GENERAL" />
      </ElSelect>
      <ElSelect v-model="filters.riskLevel" aria-label="风险等级">
        <ElOption label="全部风险" value="" />
        <ElOption label="低" value="LOW" />
        <ElOption label="中" value="MEDIUM" />
        <ElOption label="高" value="HIGH" />
        <ElOption label="严重" value="CRITICAL" />
      </ElSelect>
      <ElInput v-model="filters.tag" clearable placeholder="精确标签" aria-label="资产标签" />
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="assets" @current-change="selectedAsset = $event as DeploymentAsset | null">
        <ElTableColumn label="资产" min-width="220">
          <template #default="scope">
            <strong>{{ scope.row.name }}</strong>
            <span class="cell-secondary">{{ typeLabel(scope.row.assetType) }} · {{ scope.row.versionLabel }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="系统 / 架构" min-width="150">
          <template #default="scope">{{ scope.row.operatingSystem || '通用' }} / {{ scope.row.architecture || '通用' }}</template>
        </ElTableColumn>
        <ElTableColumn label="环境" width="100">
          <template #default="scope">{{ environmentLabel(scope.row.environment) }}</template>
        </ElTableColumn>
        <ElTableColumn label="风险" width="90">
          <template #default="scope"><ElTag :type="riskType(scope.row.riskLevel)" effect="plain">{{ riskLabel(scope.row.riskLevel) }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn label="文件" min-width="180" prop="file.originalName" />
        <ElTableColumn label="操作" width="170">
          <template #default="scope">
            <ElButton link type="primary" @click.stop="selectAsset(scope.row)">详情</ElButton>
            <ElButton link @click.stop="showRowVersions(scope.row)">版本</ElButton>
            <ElButton v-if="canWrite" link @click.stop="openRowVersionEditor(scope.row)">新版本</ElButton>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无部署资产" :image-size="64" /></template>
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
      @current-change="loadAssets"
    />

    <section v-if="selectedAsset" class="asset-detail" aria-label="部署资产详情">
      <div class="subpanel-heading">
        <div><p class="section-label">资产详情</p><h3>{{ selectedAsset.name }} · {{ selectedAsset.versionLabel }}</h3></div>
        <ElButton text @click="selectedAsset = null">收起</ElButton>
      </div>
      <dl class="detail-grid">
        <div><dt>类型</dt><dd>{{ typeLabel(selectedAsset.assetType) }}</dd></div>
        <div><dt>内部版本</dt><dd>{{ selectedAsset.version }}</dd></div>
        <div><dt>文件</dt><dd>{{ selectedAsset.file.originalName }}</dd></div>
        <div><dt>标签</dt><dd>{{ selectedAsset.tags.join('、') || '未设置' }}</dd></div>
      </dl>
      <p v-if="selectedAsset.description">{{ selectedAsset.description }}</p>
      <div v-if="selectedAsset.assetType === 'SCRIPT'" class="instruction-grid">
        <article><strong>前置条件</strong><p>{{ selectedAsset.prerequisites }}</p></article>
        <article><strong>执行方式</strong><p>{{ selectedAsset.executionInstructions }}</p></article>
        <article><strong>回滚说明</strong><p>{{ selectedAsset.rollbackInstructions }}</p></article>
      </div>
    </section>

    <ElDialog :model-value="editorOpen" :title="form.assetGroupId ? '创建资产新版本' : '新建部署资产'" width="min(720px, 94vw)" @close="editorOpen = false">
      <form class="asset-editor-grid" aria-label="部署资产表单" @submit.prevent="save">
        <label>资产名称<ElInput v-model="form.name" maxlength="200" /></label>
        <label>业务版本<ElInput v-model="form.versionLabel" placeholder="例如 1.0.0" maxlength="64" /></label>
        <label>资产类型
          <ElSelect v-model="form.assetType">
            <ElOption label="安装包" value="INSTALLATION_PACKAGE" /><ElOption label="脚本" value="SCRIPT" />
            <ElOption label="手册" value="MANUAL" /><ElOption label="配置模板" value="CONFIG_TEMPLATE" />
            <ElOption label="依赖组件" value="DEPENDENCY" />
          </ElSelect>
        </label>
        <label>关联文件
          <ElSelect v-model="form.fileAssetId" filterable placeholder="选择已上传完成的文件">
            <ElOption v-for="file in availableFiles" :key="file.id" :label="`${file.originalName} · v${file.version}`" :value="file.id" />
          </ElSelect>
        </label>
        <label>操作系统<ElInput v-model="form.operatingSystem" placeholder="例如 Linux" maxlength="100" /></label>
        <label>系统架构<ElInput v-model="form.architecture" placeholder="例如 amd64" maxlength="64" /></label>
        <label>目标环境
          <ElSelect v-model="form.environment"><ElOption label="开发" value="DEVELOPMENT" /><ElOption label="测试" value="TESTING" /><ElOption label="预发布" value="STAGING" /><ElOption label="生产" value="PRODUCTION" /><ElOption label="通用" value="GENERAL" /></ElSelect>
        </label>
        <label>风险等级
          <ElSelect v-model="form.riskLevel"><ElOption label="低" value="LOW" /><ElOption label="中" value="MEDIUM" /><ElOption label="高" value="HIGH" /><ElOption label="严重" value="CRITICAL" /></ElSelect>
        </label>
        <label class="asset-editor-wide">标签<ElInput v-model="tagText" placeholder="使用英文逗号分隔，最多 10 个" /></label>
        <label class="asset-editor-wide">说明<ElInput v-model="form.description" type="textarea" :rows="2" maxlength="5000" /></label>
        <template v-if="scriptSelected">
          <label class="asset-editor-wide">前置条件<ElInput v-model="form.prerequisites" type="textarea" :rows="2" /></label>
          <label class="asset-editor-wide">执行方式<ElInput v-model="form.executionInstructions" type="textarea" :rows="2" /></label>
          <label class="asset-editor-wide">回滚说明<ElInput v-model="form.rollbackInstructions" type="textarea" :rows="2" /></label>
        </template>
        <p v-if="editorError" class="content-error asset-editor-wide" role="alert">{{ editorError }}</p>
      </form>
      <template #footer>
        <ElButton @click="editorOpen = false">取消</ElButton>
        <ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存资产版本</ElButton>
      </template>
    </ElDialog>

    <ElDialog :model-value="versionsOpen" title="资产版本" width="min(760px, 94vw)" @close="versionsOpen = false">
      <ElTable :data="versions">
        <ElTableColumn prop="version" label="内部版本" width="100" />
        <ElTableColumn prop="versionLabel" label="业务版本" width="120" />
        <ElTableColumn prop="name" label="名称" min-width="180" />
        <ElTableColumn prop="file.originalName" label="关联文件" min-width="180" />
      </ElTable>
    </ElDialog>
  </section>
</template>
