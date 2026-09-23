<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'

import { ApiError } from '../api/client'
import { createDeploymentRecord, findSimilarDeployments, getDeploymentRecord, listDeploymentRecords, updateDeploymentBaseline } from '../api/deployment-records'
import { listDeploymentSolutions } from '../api/deployment-solutions'
import { listEnvironmentFingerprints } from '../api/environment-fingerprints'
import { listServers } from '../api/servers'
import type { DeploymentRecord, DeploymentRecordSummary, DeploymentResult, DeploymentSolutionSummary, EnvironmentFingerprint, Pagination, ServerRecord, SimilarDeployment } from '../types'

const props = defineProps<{ projectId: number; canWrite: boolean }>()
const records = ref<DeploymentRecordSummary[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const filters = reactive({ result: '' as '' | DeploymentResult, baseline: '' as '' | 'true' | 'false' })
const loading = ref(false)
const error = ref('')
const selected = ref<DeploymentRecord | null>(null)
const editorOpen = ref(false)
const saving = ref(false)
const editorError = ref('')
const servers = ref<ServerRecord[]>([])
const solutions = ref<DeploymentSolutionSummary[]>([])
const fingerprints = ref<EnvironmentFingerprint[]>([])
const similarOpen = ref(false)
const similarLoading = ref(false)
const similarFingerprintId = ref<number>()
const similarItems = ref<SimilarDeployment[]>([])
const form = reactive({
  serverId: undefined as number | undefined,
  solutionId: undefined as number | undefined,
  executedAt: localDateTime(),
  result: 'SUCCESS' as DeploymentResult,
  exceptionNotes: '',
  notes: '',
})
const formReady = computed(() => Boolean(
  form.serverId && form.solutionId && form.executedAt &&
  (form.result === 'SUCCESS' || form.exceptionNotes.trim()),
))

onMounted(() => loadRecords())
watch(() => props.projectId, () => {
  selected.value = null; editorOpen.value = false; similarOpen.value = false; loadRecords(1)
})

async function loadRecords(page = pagination.page): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const result = await listDeploymentRecords(props.projectId, {
      page, pageSize: pagination.pageSize, result: filters.result || undefined,
      baseline: filters.baseline === '' ? undefined : filters.baseline === 'true',
    })
    records.value = result.data; Object.assign(pagination, result.pagination)
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}

async function loadOptions(): Promise<void> {
  const [serverPage, solutionPage, fingerprintPage] = await Promise.all([
    listServers(props.projectId, { page: 1, pageSize: 100 }),
    listDeploymentSolutions(props.projectId, { page: 1, pageSize: 100, status: 'ACTIVE' }),
    listEnvironmentFingerprints(props.projectId, { page: 1, pageSize: 100 }),
  ])
  servers.value = serverPage.data; solutions.value = solutionPage.data; fingerprints.value = fingerprintPage.data
}

async function openEditor(): Promise<void> {
  Object.assign(form, { serverId: undefined, solutionId: undefined, executedAt: localDateTime(), result: 'SUCCESS', exceptionNotes: '', notes: '' })
  editorError.value = ''; editorOpen.value = true
  try { await loadOptions() } catch (reason) { editorError.value = errorMessage(reason) }
}

async function save(): Promise<void> {
  if (!formReady.value) return
  saving.value = true; editorError.value = ''
  try {
    selected.value = await createDeploymentRecord(props.projectId, {
      serverId: form.serverId!, solutionId: form.solutionId!,
      executedAt: new Date(form.executedAt).toISOString(), result: form.result,
      exceptionNotes: clean(form.exceptionNotes), notes: clean(form.notes),
    })
    editorOpen.value = false; await loadRecords(1)
  } catch (reason) { editorError.value = errorMessage(reason) } finally { saving.value = false }
}

async function showDetails(row: DeploymentRecordSummary): Promise<void> {
  error.value = ''
  try { selected.value = await getDeploymentRecord(props.projectId, row.id) }
  catch (reason) { error.value = errorMessage(reason) }
}

async function toggleBaseline(row: DeploymentRecordSummary): Promise<void> {
  error.value = ''
  try {
    await updateDeploymentBaseline(props.projectId, row.id, !row.baseline)
    await loadRecords()
    if (selected.value?.id === row.id) selected.value = await getDeploymentRecord(props.projectId, row.id)
  } catch (reason) { error.value = errorMessage(reason) }
}

function showRowDetails(row: unknown): Promise<void> {
  return showDetails(row as DeploymentRecordSummary)
}

function toggleRowBaseline(row: unknown): Promise<void> {
  return toggleBaseline(row as DeploymentRecordSummary)
}

async function openSimilar(): Promise<void> {
  similarOpen.value = true; similarItems.value = []; similarFingerprintId.value = undefined; error.value = ''
  try { await loadOptions() } catch (reason) { error.value = errorMessage(reason) }
}

async function searchSimilar(): Promise<void> {
  if (!similarFingerprintId.value) return
  similarLoading.value = true; error.value = ''
  try { similarItems.value = await findSimilarDeployments(props.projectId, similarFingerprintId.value) }
  catch (reason) { error.value = errorMessage(reason) } finally { similarLoading.value = false }
}

function resultLabel(value: DeploymentResult): string { return value === 'SUCCESS' ? '成功' : '失败' }
function clean(value: string): string | undefined { return value.trim() || undefined }
function localDateTime(): string {
  const date = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000)
  return date.toISOString().slice(0, 16)
}
function displayTime(value: string): string { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
function errorMessage(reason: unknown): string { return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试' }
</script>

<template>
  <section class="subpanel record-panel" aria-labelledby="deployment-record-title">
    <div class="subpanel-heading">
      <div><p class="section-label">执行留痕</p><h2 id="deployment-record-title">部署记录与成功基线</h2></div>
      <div class="record-heading-actions"><ElButton data-test="find-similar" @click="openSimilar">查找相似成功经验</ElButton><ElButton v-if="canWrite" data-test="create-record" type="primary" @click="openEditor">记录部署</ElButton></div>
    </div>
    <form class="record-filter" aria-label="部署记录筛选" @submit.prevent="loadRecords(1)">
      <ElSelect v-model="filters.result" aria-label="执行结果"><ElOption label="全部结果" value="" /><ElOption label="成功" value="SUCCESS" /><ElOption label="失败" value="FAILED" /></ElSelect>
      <ElSelect v-model="filters.baseline" aria-label="基线状态"><ElOption label="全部记录" value="" /><ElOption label="仅成功基线" value="true" /><ElOption label="仅非基线" value="false" /></ElSelect>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>
    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="loading"><ElTable :data="records">
      <ElTableColumn label="执行对象" min-width="230"><template #default="scope"><strong>{{ scope.row.serverName }}</strong><span class="cell-secondary">{{ scope.row.solutionName }}</span></template></ElTableColumn>
      <ElTableColumn label="结果" width="90"><template #default="scope"><ElTag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ resultLabel(scope.row.result) }}</ElTag></template></ElTableColumn>
      <ElTableColumn label="执行人" prop="executedByName" width="120" /><ElTableColumn label="执行时间" min-width="175"><template #default="scope">{{ displayTime(scope.row.executedAt) }}</template></ElTableColumn>
      <ElTableColumn label="基线" width="90"><template #default="scope"><ElTag v-if="scope.row.baseline" type="primary">成功基线</ElTag><span v-else>—</span></template></ElTableColumn>
      <ElTableColumn label="操作" width="190"><template #default="scope"><ElButton data-test="view-record" link type="primary" @click="showRowDetails(scope.row)">详情</ElButton><ElButton v-if="canWrite && scope.row.result === 'SUCCESS'" data-test="toggle-baseline" link @click="toggleRowBaseline(scope.row)">{{ scope.row.baseline ? '取消基线' : '设为基线' }}</ElButton></template></ElTableColumn>
      <template #empty><ElEmpty description="暂无部署记录" :image-size="64" /></template>
    </ElTable></div>
    <ElPagination v-if="pagination.totalPages > 1" class="pagination" layout="prev, pager, next" :current-page="pagination.page" :page-size="pagination.pageSize" :total="pagination.totalItems" @current-change="loadRecords" />

    <section v-if="selected" class="record-detail" aria-label="部署记录详情"><div class="subpanel-heading"><div><p class="section-label">历史快照</p><h3>{{ selected.serverSnapshot.name }} · {{ selected.solutionSnapshot.name }}</h3></div><ElButton text @click="selected = null">收起</ElButton></div>
      <dl class="detail-grid"><div><dt>执行结果</dt><dd>{{ resultLabel(selected.result) }}</dd></div><div><dt>执行人</dt><dd>{{ selected.executedByName }}</dd></div><div><dt>操作系统</dt><dd>{{ selected.environmentSnapshot.operatingSystem }} {{ selected.environmentSnapshot.osVersion || '' }}</dd></div><div><dt>运行时</dt><dd>{{ selected.environmentSnapshot.runtimeName || '未配置' }} {{ selected.environmentSnapshot.runtimeVersion || '' }}</dd></div><div><dt>数据库</dt><dd>{{ selected.environmentSnapshot.databaseName || '未配置' }} {{ selected.environmentSnapshot.databaseVersion || '' }}</dd></div><div><dt>备注</dt><dd>{{ selected.notes || '—' }}</dd></div></dl>
      <p v-if="selected.exceptionNotes" class="content-error">异常：{{ selected.exceptionNotes }}</p>
      <ol class="solution-steps"><li v-for="step in selected.solutionSnapshot.steps" :key="step.id"><strong>{{ step.stepOrder }}. {{ step.title }}</strong><p>{{ step.instructions }}</p><span>{{ step.assetName }} · {{ step.assetVersionLabel }}</span></li></ol>
    </section>

    <ElDialog :model-value="editorOpen" title="记录部署执行" width="min(700px, 96vw)" @close="editorOpen = false"><form class="record-editor" aria-label="部署记录表单" @submit.prevent="save">
      <label>目标服务器<ElSelect v-model="form.serverId" filterable placeholder="请选择服务器"><ElOption v-for="server in servers" :key="server.id" :label="`${server.name} · ${server.host}`" :value="server.id" /></ElSelect></label>
      <label>部署方案<ElSelect v-model="form.solutionId" filterable placeholder="请选择启用方案"><ElOption v-for="solution in solutions" :key="solution.id" :label="`${solution.name} · ${solution.fingerprintName}`" :value="solution.id" /></ElSelect></label>
      <label>执行时间<input v-model="form.executedAt" class="native-field" type="datetime-local" /></label>
      <label>执行结果<ElSelect v-model="form.result"><ElOption label="成功" value="SUCCESS" /><ElOption label="失败" value="FAILED" /></ElSelect></label>
      <label v-if="form.result === 'FAILED'" class="record-editor-wide">异常说明<ElInput v-model="form.exceptionNotes" type="textarea" :rows="3" maxlength="5000" /></label>
      <label class="record-editor-wide">执行备注<ElInput v-model="form.notes" type="textarea" :rows="3" maxlength="5000" /></label>
      <p v-if="editorError" class="content-error record-editor-wide" role="alert">{{ editorError }}</p>
    </form><template #footer><ElButton @click="editorOpen = false">取消</ElButton><ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存记录</ElButton></template></ElDialog>

    <ElDialog :model-value="similarOpen" title="查找相似环境的成功基线" width="min(820px, 96vw)" @close="similarOpen = false"><div class="similar-search"><ElSelect v-model="similarFingerprintId" filterable placeholder="选择当前环境指纹"><ElOption v-for="fingerprint in fingerprints" :key="fingerprint.id" :label="fingerprint.name" :value="fingerprint.id" /></ElSelect><ElButton type="primary" :disabled="!similarFingerprintId" :loading="similarLoading" @click="searchSimilar">开始匹配</ElButton></div>
      <ElEmpty v-if="!similarItems.length && !similarLoading" description="选择环境指纹后，从成功基线中查找经验" :image-size="64" />
      <article v-for="item in similarItems" :key="item.record.id" class="similar-card"><div><strong>{{ item.record.solutionName }}</strong><span>{{ item.record.serverName }} · {{ displayTime(item.record.executedAt) }}</span></div><ElTag type="success">{{ item.score }} 分</ElTag><p>相同：{{ item.matchedFields.join('；') || '无' }}</p><p>差异：{{ item.differentFields.join('；') || '无' }}</p></article>
    </ElDialog>
  </section>
</template>
