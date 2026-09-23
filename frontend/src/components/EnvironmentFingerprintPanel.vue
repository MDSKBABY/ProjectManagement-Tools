<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElInput, ElOption, ElPagination, ElPopconfirm, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'

import { ApiError } from '../api/client'
import { createEnvironmentFingerprint, deleteEnvironmentFingerprint, listEnvironmentFingerprints, updateEnvironmentFingerprint } from '../api/environment-fingerprints'
import type { DeploymentEnvironment, EnvironmentFingerprint, Pagination, SaveEnvironmentFingerprintInput } from '../types'

const props = defineProps<{ projectId: number; canWrite: boolean }>()
const fingerprints = ref<EnvironmentFingerprint[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({ keyword: '', environment: '' as '' | DeploymentEnvironment, operatingSystem: '', architecture: '', databaseName: '', middleware: '', tag: '' })
const loading = ref(false)
const error = ref('')
const editorOpen = ref(false)
const editing = ref<EnvironmentFingerprint | null>(null)
const saving = ref(false)
const editorError = ref('')
const middlewaresText = ref('')
const tagsText = ref('')

const emptyForm = (): SaveEnvironmentFingerprintInput => ({
  name: '', environment: 'GENERAL', operatingSystem: '', osVersion: '', kernelVersion: '',
  architecture: '', runtimeName: '', runtimeVersion: '', databaseName: '', databaseVersion: '',
  middlewares: [], networkZone: '', tags: [], notes: '',
})
const form = reactive<SaveEnvironmentFingerprintInput>(emptyForm())
const formReady = computed(() => Boolean(form.name.trim() && form.operatingSystem.trim() && form.architecture.trim()))

onMounted(() => loadFingerprints())
watch(() => props.projectId, () => { editorOpen.value = false; loadFingerprints(1) })

async function loadFingerprints(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listEnvironmentFingerprints(props.projectId, {
      page, pageSize: pagination.pageSize, keyword: clean(filters.keyword),
      environment: filters.environment || undefined, operatingSystem: clean(filters.operatingSystem),
      architecture: clean(filters.architecture), databaseName: clean(filters.databaseName),
      middleware: clean(filters.middleware), tag: clean(filters.tag),
    })
    fingerprints.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}

function openEditor(source?: EnvironmentFingerprint): void {
  editing.value = source ?? null
  Object.assign(form, emptyForm(), source ? {
    name: source.name, environment: source.environment, operatingSystem: source.operatingSystem,
    osVersion: source.osVersion ?? '', kernelVersion: source.kernelVersion ?? '', architecture: source.architecture,
    runtimeName: source.runtimeName ?? '', runtimeVersion: source.runtimeVersion ?? '',
    databaseName: source.databaseName ?? '', databaseVersion: source.databaseVersion ?? '',
    middlewares: [...source.middlewares], networkZone: source.networkZone ?? '', tags: [...source.tags], notes: source.notes ?? '',
  } : {})
  middlewaresText.value = source?.middlewares.join(', ') ?? ''
  tagsText.value = source?.tags.join(', ') ?? ''
  editorError.value = ''
  editorOpen.value = true
}

async function save(): Promise<void> {
  if (!formReady.value) return
  saving.value = true
  editorError.value = ''
  const input = { ...form, name: form.name.trim(), operatingSystem: form.operatingSystem.trim(), architecture: form.architecture.trim(), middlewares: split(middlewaresText.value, 20), tags: split(tagsText.value, 10) }
  try {
    if (editing.value) await updateEnvironmentFingerprint(props.projectId, editing.value.id, input)
    else await createEnvironmentFingerprint(props.projectId, input)
    editorOpen.value = false
    await loadFingerprints(1)
  } catch (reason) { editorError.value = errorMessage(reason) } finally { saving.value = false }
}

async function remove(fingerprint: EnvironmentFingerprint): Promise<void> {
  error.value = ''
  try { await deleteEnvironmentFingerprint(props.projectId, fingerprint.id); await loadFingerprints(1) }
  catch (reason) { error.value = errorMessage(reason) }
}

function openRowEditor(row: unknown): void { openEditor(row as EnvironmentFingerprint) }
function removeRow(row: unknown): Promise<void> { return remove(row as EnvironmentFingerprint) }

function environmentLabel(value: DeploymentEnvironment): string {
  return { DEVELOPMENT: '开发', TESTING: '测试', STAGING: '预发布', PRODUCTION: '生产', GENERAL: '通用' }[value]
}
function clean(value: string): string | undefined { return value.trim() || undefined }
function split(value: string, limit: number): string[] { return value.split(',').map((item) => item.trim()).filter((item, index, all) => item && all.indexOf(item) === index).slice(0, limit) }
function errorMessage(reason: unknown): string { return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试' }
</script>

<template>
  <section class="subpanel planning-panel" aria-labelledby="fingerprint-title">
    <div class="subpanel-heading"><div><p class="section-label">环境基线</p><h2 id="fingerprint-title">环境指纹</h2></div>
      <ElButton v-if="canWrite" data-test="create-fingerprint" type="primary" @click="openEditor()">新建指纹</ElButton>
    </div>
    <form class="planning-filter-grid" aria-label="环境指纹筛选" @submit.prevent="loadFingerprints(1)">
      <ElInput v-model="filters.keyword" clearable placeholder="名称或说明" aria-label="搜索环境指纹" />
      <ElSelect v-model="filters.environment" aria-label="环境"><ElOption label="全部环境" value="" /><ElOption label="开发" value="DEVELOPMENT" /><ElOption label="测试" value="TESTING" /><ElOption label="预发布" value="STAGING" /><ElOption label="生产" value="PRODUCTION" /><ElOption label="通用" value="GENERAL" /></ElSelect>
      <ElInput v-model="filters.operatingSystem" clearable placeholder="操作系统" /><ElInput v-model="filters.architecture" clearable placeholder="架构" />
      <ElInput v-model="filters.databaseName" clearable placeholder="数据库" /><ElInput v-model="filters.middleware" clearable placeholder="中间件" />
      <ElInput v-model="filters.tag" clearable placeholder="标签" /><ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>
    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading"><ElTable :data="fingerprints">
      <ElTableColumn label="指纹" min-width="210"><template #default="scope"><strong>{{ scope.row.name }}</strong><span class="cell-secondary">{{ environmentLabel(scope.row.environment) }} · {{ scope.row.networkZone || '未设置网络区' }}</span></template></ElTableColumn>
      <ElTableColumn label="系统 / 架构" min-width="170"><template #default="scope">{{ scope.row.operatingSystem }} {{ scope.row.osVersion || '' }} / {{ scope.row.architecture }}</template></ElTableColumn>
      <ElTableColumn label="运行时 / 数据库" min-width="200"><template #default="scope"><span>{{ scope.row.runtimeName || '未设置' }} {{ scope.row.runtimeVersion || '' }}</span><span class="cell-secondary">{{ scope.row.databaseName || '未设置' }} {{ scope.row.databaseVersion || '' }}</span></template></ElTableColumn>
      <ElTableColumn label="中间件" min-width="150"><template #default="scope">{{ scope.row.middlewares.join('、') || '未设置' }}</template></ElTableColumn>
      <ElTableColumn label="标签" min-width="120"><template #default="scope"><ElTag v-for="tag in scope.row.tags" :key="tag" effect="plain">{{ tag }}</ElTag><span v-if="!scope.row.tags.length">—</span></template></ElTableColumn>
      <ElTableColumn v-if="canWrite" label="操作" width="130"><template #default="scope"><ElButton data-test="edit-fingerprint" link type="primary" @click="openRowEditor(scope.row)">编辑</ElButton><ElPopconfirm title="确认删除该环境指纹？" @confirm="removeRow(scope.row)"><template #reference><ElButton link type="danger">删除</ElButton></template></ElPopconfirm></template></ElTableColumn>
      <template #empty><ElEmpty description="暂无环境指纹" :image-size="64" /></template>
    </ElTable></div>
    <ElPagination v-if="pagination.totalPages > 1" class="pagination" layout="prev, pager, next" :current-page="pagination.page" :page-size="pagination.pageSize" :total="pagination.totalItems" @current-change="loadFingerprints" />

    <ElDialog :model-value="editorOpen" :title="editing ? '编辑环境指纹' : '新建环境指纹'" width="min(760px, 94vw)" @close="editorOpen = false">
      <form class="planning-editor-grid" aria-label="环境指纹表单" @submit.prevent="save">
        <label>指纹名称<ElInput v-model="form.name" maxlength="120" /></label><label>环境<ElSelect v-model="form.environment"><ElOption label="开发" value="DEVELOPMENT" /><ElOption label="测试" value="TESTING" /><ElOption label="预发布" value="STAGING" /><ElOption label="生产" value="PRODUCTION" /><ElOption label="通用" value="GENERAL" /></ElSelect></label>
        <label>操作系统<ElInput v-model="form.operatingSystem" maxlength="100" /></label><label>系统版本<ElInput v-model="form.osVersion" maxlength="100" /></label>
        <label>内核版本<ElInput v-model="form.kernelVersion" maxlength="100" /></label><label>架构<ElInput v-model="form.architecture" maxlength="64" /></label>
        <label>运行时<ElInput v-model="form.runtimeName" maxlength="100" /></label><label>运行时版本<ElInput v-model="form.runtimeVersion" maxlength="100" /></label>
        <label>数据库<ElInput v-model="form.databaseName" maxlength="100" /></label><label>数据库版本<ElInput v-model="form.databaseVersion" maxlength="100" /></label>
        <label>网络区<ElInput v-model="form.networkZone" maxlength="100" /></label><label>中间件<ElInput v-model="middlewaresText" placeholder="英文逗号分隔" /></label>
        <label class="planning-editor-wide">标签<ElInput v-model="tagsText" placeholder="英文逗号分隔" /></label><label class="planning-editor-wide">说明<ElInput v-model="form.notes" type="textarea" :rows="3" maxlength="5000" /></label>
        <p v-if="editorError" class="content-error planning-editor-wide" role="alert">{{ editorError }}</p>
      </form>
      <template #footer><ElButton @click="editorOpen = false">取消</ElButton><ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存指纹</ElButton></template>
    </ElDialog>
  </section>
</template>
