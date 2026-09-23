<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElInput, ElInputNumber, ElOption, ElPagination, ElPopconfirm, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'

import { ApiError } from '../api/client'
import { listDeploymentAssets } from '../api/deployment-assets'
import { createDeploymentSolution, deleteDeploymentSolution, getDeploymentSolution, listDeploymentSolutions, updateDeploymentSolution } from '../api/deployment-solutions'
import { listEnvironmentFingerprints } from '../api/environment-fingerprints'
import type { DeploymentAsset, DeploymentSolution, DeploymentSolutionStatus, DeploymentSolutionSummary, EnvironmentFingerprint, Pagination, SaveDeploymentSolutionInput, SaveDeploymentSolutionStepInput } from '../types'

type SolutionEditorStep = Omit<SaveDeploymentSolutionStepInput, 'assetId'> & {
  assetId: number | undefined
}

type SolutionEditorForm = Omit<SaveDeploymentSolutionInput, 'fingerprintId' | 'steps'> & {
  fingerprintId: number | undefined
  steps: SolutionEditorStep[]
}

const props = defineProps<{ projectId: number; canWrite: boolean }>()
const solutions = ref<DeploymentSolutionSummary[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({ keyword: '', status: '' as '' | DeploymentSolutionStatus })
const loading = ref(false)
const error = ref('')
const selected = ref<DeploymentSolution | null>(null)
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const editorError = ref('')
const fingerprints = ref<EnvironmentFingerprint[]>([])
const assets = ref<DeploymentAsset[]>([])

const emptyStep = (order: number): SolutionEditorStep => ({ stepOrder: order, title: '', instructions: '', assetId: undefined, parametersTemplate: '' })
const emptyForm = (): SolutionEditorForm => ({
  name: '', scenario: '', fingerprintId: undefined, architectureDescription: '', prerequisites: '',
  rollbackSteps: '', riskNotes: '', status: 'DRAFT', steps: [emptyStep(1)],
})
const form = reactive<SolutionEditorForm>(emptyForm())
const formReady = computed(() => Boolean(
  form.name.trim() && form.scenario.trim() && form.fingerprintId && form.architectureDescription.trim() &&
  form.prerequisites.trim() && form.rollbackSteps.trim() && form.riskNotes.trim() && form.steps.length &&
  form.steps.every((step) => step.title.trim() && step.instructions.trim() && (step.assetId ?? 0) > 0),
))

onMounted(() => loadSolutions())
watch(() => props.projectId, () => { selected.value = null; editorOpen.value = false; loadSolutions(1) })

async function loadSolutions(page = pagination.page): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const result = await listDeploymentSolutions(props.projectId, { page, pageSize: pagination.pageSize, keyword: clean(filters.keyword), status: filters.status || undefined })
    solutions.value = result.data; Object.assign(pagination, result.pagination)
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}

async function showDetails(summary: DeploymentSolutionSummary): Promise<void> {
  error.value = ''
  try { selected.value = await getDeploymentSolution(props.projectId, summary.id) }
  catch (reason) { error.value = errorMessage(reason) }
}

async function loadOptions(): Promise<void> {
  const [fingerprintPage, assetPage] = await Promise.all([
    listEnvironmentFingerprints(props.projectId, { page: 1, pageSize: 100 }),
    listDeploymentAssets(props.projectId, { page: 1, pageSize: 100 }),
  ])
  fingerprints.value = fingerprintPage.data
  assets.value = assetPage.data
}

async function openEditor(source?: DeploymentSolutionSummary): Promise<void> {
  editingId.value = source?.id ?? null; Object.assign(form, emptyForm()); editorError.value = ''; editorOpen.value = true
  try {
    await loadOptions()
    if (source) {
      const detail = await getDeploymentSolution(props.projectId, source.id)
      Object.assign(form, {
        name: detail.name, scenario: detail.scenario, fingerprintId: detail.fingerprintId,
        architectureDescription: detail.architectureDescription, prerequisites: detail.prerequisites,
        rollbackSteps: detail.rollbackSteps, riskNotes: detail.riskNotes, status: detail.status,
        steps: detail.steps.map((step) => ({ stepOrder: step.stepOrder, title: step.title, instructions: step.instructions, assetId: step.assetId, parametersTemplate: step.parametersTemplate ?? '' })),
      })
    }
  } catch (reason) { editorError.value = errorMessage(reason) }
}

function addStep(): void { form.steps.push(emptyStep(form.steps.length + 1)) }
function removeStep(index: number): void {
  if (form.steps.length === 1) return
  form.steps.splice(index, 1)
  form.steps.forEach((step, stepIndex) => { step.stepOrder = stepIndex + 1 })
}

async function save(): Promise<void> {
  if (!formReady.value) return
  saving.value = true; editorError.value = ''
  const input: SaveDeploymentSolutionInput = {
    ...form,
    fingerprintId: form.fingerprintId!,
    name: form.name.trim(),
    scenario: form.scenario.trim(),
    steps: form.steps.map((step, index) => ({
      ...step,
      assetId: step.assetId!,
      stepOrder: index + 1,
      title: step.title.trim(),
      instructions: step.instructions.trim(),
    })),
  }
  try {
    const result = editingId.value
      ? await updateDeploymentSolution(props.projectId, editingId.value, input)
      : await createDeploymentSolution(props.projectId, input)
    selected.value = result; editorOpen.value = false; await loadSolutions(1)
  } catch (reason) { editorError.value = errorMessage(reason) } finally { saving.value = false }
}

async function remove(summary: DeploymentSolutionSummary): Promise<void> {
  error.value = ''
  try { await deleteDeploymentSolution(props.projectId, summary.id); if (selected.value?.id === summary.id) selected.value = null; await loadSolutions(1) }
  catch (reason) { error.value = errorMessage(reason) }
}

function showRowDetails(row: unknown): Promise<void> { return showDetails(row as DeploymentSolutionSummary) }
function openRowEditor(row: unknown): Promise<void> { return openEditor(row as DeploymentSolutionSummary) }
function removeRow(row: unknown): Promise<void> { return remove(row as DeploymentSolutionSummary) }

function statusLabel(value: DeploymentSolutionStatus): string { return { DRAFT: '草稿', ACTIVE: '启用', ARCHIVED: '已归档' }[value] }
function statusType(value: DeploymentSolutionStatus): 'info' | 'success' | 'warning' { return { DRAFT: 'info' as const, ACTIVE: 'success' as const, ARCHIVED: 'warning' as const }[value] }
function clean(value: string): string | undefined { return value.trim() || undefined }
function errorMessage(reason: unknown): string { return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试' }
</script>

<template>
  <section class="subpanel planning-panel" aria-labelledby="solution-title">
    <div class="subpanel-heading"><div><p class="section-label">标准化执行</p><h2 id="solution-title">部署方案</h2></div><ElButton v-if="canWrite" data-test="create-solution" type="primary" @click="openEditor()">新建方案</ElButton></div>
    <form class="solution-filter" aria-label="部署方案筛选" @submit.prevent="loadSolutions(1)"><ElInput v-model="filters.keyword" clearable placeholder="名称或适用场景" /><ElSelect v-model="filters.status" aria-label="方案状态"><ElOption label="全部状态" value="" /><ElOption label="草稿" value="DRAFT" /><ElOption label="启用" value="ACTIVE" /><ElOption label="已归档" value="ARCHIVED" /></ElSelect><ElButton native-type="submit" :loading="loading">查询</ElButton></form>
    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading"><ElTable :data="solutions">
      <ElTableColumn label="方案" min-width="230"><template #default="scope"><strong>{{ scope.row.name }}</strong><span class="cell-secondary">{{ scope.row.scenario }}</span></template></ElTableColumn>
      <ElTableColumn prop="fingerprintName" label="环境指纹" min-width="150" /><ElTableColumn prop="stepCount" label="步骤" width="80" />
      <ElTableColumn label="状态" width="90"><template #default="scope"><ElTag :type="statusType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</ElTag></template></ElTableColumn>
      <ElTableColumn label="操作" width="170"><template #default="scope"><ElButton data-test="view-solution" link type="primary" @click="showRowDetails(scope.row)">详情</ElButton><ElButton v-if="canWrite" data-test="edit-solution" link @click="openRowEditor(scope.row)">编辑</ElButton><ElPopconfirm v-if="canWrite" title="确认删除该部署方案？" @confirm="removeRow(scope.row)"><template #reference><ElButton link type="danger">删除</ElButton></template></ElPopconfirm></template></ElTableColumn>
      <template #empty><ElEmpty description="暂无部署方案" :image-size="64" /></template>
    </ElTable></div>
    <ElPagination v-if="pagination.totalPages > 1" class="pagination" layout="prev, pager, next" :current-page="pagination.page" :page-size="pagination.pageSize" :total="pagination.totalItems" @current-change="loadSolutions" />

    <section v-if="selected" class="solution-detail" aria-label="部署方案详情"><div class="subpanel-heading"><div><p class="section-label">方案详情</p><h3>{{ selected.name }}</h3></div><ElButton text @click="selected = null">收起</ElButton></div>
      <dl class="detail-grid"><div><dt>环境指纹</dt><dd>{{ selected.fingerprintName }}</dd></div><div><dt>状态</dt><dd>{{ statusLabel(selected.status) }}</dd></div><div><dt>适用场景</dt><dd>{{ selected.scenario }}</dd></div><div><dt>架构说明</dt><dd>{{ selected.architectureDescription }}</dd></div></dl>
      <div class="instruction-grid"><article><strong>前置条件</strong><p>{{ selected.prerequisites }}</p></article><article><strong>回滚步骤</strong><p>{{ selected.rollbackSteps }}</p></article><article><strong>风险提示</strong><p>{{ selected.riskNotes }}</p></article></div>
      <ol class="solution-steps"><li v-for="step in selected.steps" :key="step.id"><strong>{{ step.stepOrder }}. {{ step.title }}</strong><p>{{ step.instructions }}</p><span>{{ step.assetName }} · {{ step.assetVersionLabel }}</span><code v-if="step.parametersTemplate">{{ step.parametersTemplate }}</code></li></ol>
    </section>

    <ElDialog :model-value="editorOpen" :title="editingId ? '编辑部署方案' : '新建部署方案'" width="min(880px, 96vw)" @close="editorOpen = false">
      <form class="planning-editor-grid" aria-label="部署方案表单" @submit.prevent="save">
        <label>方案名称<ElInput v-model="form.name" maxlength="200" /></label><label>状态<ElSelect v-model="form.status"><ElOption label="草稿" value="DRAFT" /><ElOption label="启用" value="ACTIVE" /><ElOption label="已归档" value="ARCHIVED" /></ElSelect></label>
        <label class="planning-editor-wide">适用场景<ElInput v-model="form.scenario" maxlength="1000" /></label><label class="planning-editor-wide">环境指纹<ElSelect v-model="form.fingerprintId" filterable placeholder="请选择环境指纹"><ElOption v-for="item in fingerprints" :key="item.id" :label="item.name" :value="item.id" /></ElSelect></label>
        <label class="planning-editor-wide">架构说明<ElInput v-model="form.architectureDescription" type="textarea" :rows="2" /></label><label class="planning-editor-wide">前置条件<ElInput v-model="form.prerequisites" type="textarea" :rows="2" /></label>
        <label class="planning-editor-wide">回滚步骤<ElInput v-model="form.rollbackSteps" type="textarea" :rows="2" /></label><label class="planning-editor-wide">风险提示<ElInput v-model="form.riskNotes" type="textarea" :rows="2" /></label>
        <fieldset class="planning-editor-wide step-editor"><legend>执行步骤与资产版本</legend><article v-for="(step, index) in form.steps" :key="index"><div class="step-heading"><strong>步骤 {{ index + 1 }}</strong><ElButton v-if="form.steps.length > 1" text type="danger" @click="removeStep(index)">移除</ElButton></div><div class="step-grid"><label>顺序<ElInputNumber v-model="step.stepOrder" :min="1" disabled /></label><label>标题<ElInput v-model="step.title" maxlength="200" /></label><label class="planning-editor-wide">精确资产版本<ElSelect v-model="step.assetId" filterable placeholder="请选择资产版本"><ElOption v-for="asset in assets" :key="asset.id" :label="`${asset.name} · ${asset.versionLabel}`" :value="asset.id" /></ElSelect></label><label class="planning-editor-wide">执行说明<ElInput v-model="step.instructions" type="textarea" :rows="2" /></label><label class="planning-editor-wide">参数模板<ElInput v-model="step.parametersTemplate" type="textarea" :rows="2" /></label></div></article><ElButton plain @click="addStep">添加步骤</ElButton></fieldset>
        <p v-if="editorError" class="content-error planning-editor-wide" role="alert">{{ editorError }}</p>
      </form><template #footer><ElButton @click="editorOpen = false">取消</ElButton><ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存方案</ElButton></template>
    </ElDialog>
  </section>
</template>
