<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton, ElDatePicker, ElDialog, ElEmpty, ElInput, ElInputNumber,
  ElOption, ElSelect, ElTable, ElTableColumn, ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import { listDeploymentRecords } from '../api/deployment-records'
import { listMeetingRecords } from '../api/delivery-records'
import {
  confirmDailyReport, createDailyReport, listDailyReports,
  polishDailyReport, updateDailyReport,
} from '../api/daily-reports'
import { listWorkItems } from '../api/work-items'
import type {
  DailyReport, DeploymentRecordSummary, MeetingRecordSummary, SaveDailyReportInput, WorkItem,
} from '../types'

const props = defineProps<{
  projectId: number
  currentUserId: number
  canWrite: boolean
  canConfirm: boolean
}>()

const reports = ref<DailyReport[]>([])
const workItems = ref<WorkItem[]>([])
const meetings = ref<MeetingRecordSummary[]>([])
const deployments = ref<DeploymentRecordSummary[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editorError = ref('')
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const filters = reactive({ from: '', to: '' })
const form = reactive<SaveDailyReportInput>({
  reportDate: today(), originalContent: '', polishedContent: '', workHours: 8,
  workItemIds: [], meetingRecordIds: [], deploymentRecordIds: [],
})

onMounted(load)
watch(() => props.projectId, () => load())

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    reports.value = await listDailyReports(props.projectId, {
      from: filters.from || undefined, to: filters.to || undefined,
    })
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function loadOptions(): Promise<void> {
  const [items, meetingRecords, records] = await Promise.all([
    listWorkItems(props.projectId, { page: 1, pageSize: 100 }),
    listMeetingRecords(props.projectId),
    listDeploymentRecords(props.projectId, { page: 1, pageSize: 100 }),
  ])
  workItems.value = items.data
  meetings.value = meetingRecords
  deployments.value = records.data
}

async function openCreate(): Promise<void> {
  editingId.value = null
  Object.assign(form, {
    reportDate: today(), originalContent: '', polishedContent: '', workHours: 8,
    workItemIds: [], meetingRecordIds: [], deploymentRecordIds: [],
  })
  editorError.value = ''
  await loadOptionsSafely()
  editorOpen.value = true
}

async function openEdit(report: DailyReport | any): Promise<void> {
  editingId.value = report.id
  Object.assign(form, {
    reportDate: report.reportDate,
    originalContent: report.originalContent,
    polishedContent: report.polishedContent ?? '',
    workHours: report.workHours,
    workItemIds: report.workItems.map((item: { id: number }) => item.id),
    meetingRecordIds: report.meetingRecords.map((item: { id: number }) => item.id),
    deploymentRecordIds: report.deploymentRecords.map((item: { id: number }) => item.id),
  })
  editorError.value = ''
  await loadOptionsSafely()
  editorOpen.value = true
}

async function loadOptionsSafely(): Promise<void> {
  try { await loadOptions() } catch (reason) { editorError.value = errorMessage(reason) }
}

async function save(): Promise<void> {
  if (!form.reportDate || !form.originalContent.trim()) return
  saving.value = true
  editorError.value = ''
  try {
    const input = { ...form, originalContent: form.originalContent.trim(),
      polishedContent: form.polishedContent?.trim() || undefined }
    if (editingId.value) await updateDailyReport(props.projectId, editingId.value, input)
    else await createDailyReport(props.projectId, input)
    editorOpen.value = false
    await load()
  } catch (reason) {
    editorError.value = errorMessage(reason)
  } finally { saving.value = false }
}

async function polish(report: DailyReport | any): Promise<void> {
  await action(async () => { await polishDailyReport(props.projectId, report.id) })
}

async function confirm(report: DailyReport | any): Promise<void> {
  await action(async () => { await confirmDailyReport(props.projectId, report.id) })
}

async function action(callback: () => Promise<void>): Promise<void> {
  saving.value = true
  error.value = ''
  try { await callback(); await load() } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

function today(): string { return new Date().toLocaleDateString('sv-SE') }
function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '日报操作失败，请稍后重试'
}
</script>

<template>
  <section class="subpanel daily-report-panel" aria-labelledby="daily-report-title">
    <div class="subpanel-heading">
      <div>
        <p class="section-label">实施过程留痕</p>
        <h2 id="daily-report-title">项目日报</h2>
        <p>原始内容与 AI 润色稿分开保存；确认后不可覆盖。</p>
      </div>
      <ElButton v-if="canWrite" data-test="create-daily-report" type="primary" @click="openCreate">填写日报</ElButton>
    </div>

    <form class="daily-filters" @submit.prevent="load">
      <ElDatePicker v-model="filters.from" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
      <ElDatePicker v-model="filters.to" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>
    <p v-if="error" class="content-error" role="alert">{{ error }}</p>

    <ElTable :data="reports" :aria-busy="loading">
      <ElTableColumn label="日期" prop="reportDate" width="120" />
      <ElTableColumn label="填报人" prop="reporterDisplayName" width="130" />
      <ElTableColumn label="工作内容" min-width="280">
        <template #default="scope">
          <strong>{{ scope.row.polishedContent || scope.row.originalContent }}</strong>
          <span v-if="scope.row.polishedContent" class="cell-secondary">已生成 AI 润色稿</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="工时" width="80"><template #default="scope">{{ scope.row.workHours }}h</template></ElTableColumn>
      <ElTableColumn label="状态" width="100">
        <template #default="scope"><ElTag :type="scope.row.status === 'CONFIRMED' ? 'success' : 'info'">{{ scope.row.status === 'CONFIRMED' ? '已确认' : '草稿' }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn label="操作" min-width="260">
        <template #default="scope">
          <template v-if="scope.row.status === 'DRAFT'">
            <ElButton v-if="canWrite && (scope.row.reporterId === currentUserId || canConfirm)" link @click="openEdit(scope.row)">编辑</ElButton>
            <ElButton v-if="canWrite && (scope.row.reporterId === currentUserId || canConfirm)" data-test="polish-daily-report" link type="primary" :loading="saving" @click="polish(scope.row)">AI 润色</ElButton>
            <ElButton v-if="canConfirm" data-test="confirm-daily-report" link type="success" :loading="saving" @click="confirm(scope.row)">确认</ElButton>
          </template>
        </template>
      </ElTableColumn>
      <template #empty><ElEmpty description="暂无日报" :image-size="64" /></template>
    </ElTable>

    <ElDialog :model-value="editorOpen" :title="editingId ? '编辑日报草稿' : '填写日报'" width="min(760px, 94vw)" @close="editorOpen = false">
      <form class="daily-editor" @submit.prevent="save">
        <label><span>日报日期</span><ElDatePicker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD" /></label>
        <label><span>工时</span><ElInputNumber v-model="form.workHours" :min="0" :max="24" :step="0.5" /></label>
        <label class="wide"><span>原始工作内容</span><ElInput v-model="form.originalContent" type="textarea" :rows="6" maxlength="20000" show-word-limit /></label>
        <label class="wide"><span>AI 润色稿（可手工修订）</span><ElInput v-model="form.polishedContent" type="textarea" :rows="5" maxlength="20000" /></label>
        <label class="wide"><span>关联工作项</span><ElSelect v-model="form.workItemIds" multiple filterable><ElOption v-for="item in workItems" :key="item.id" :label="item.title" :value="item.id" /></ElSelect></label>
        <label class="wide"><span>关联会议 / 培训</span><ElSelect v-model="form.meetingRecordIds" multiple filterable><ElOption v-for="meeting in meetings" :key="meeting.id" :label="meeting.title" :value="meeting.id" /></ElSelect></label>
        <label class="wide"><span>关联部署记录</span><ElSelect v-model="form.deploymentRecordIds" multiple filterable><ElOption v-for="record in deployments" :key="record.id" :label="`#${record.id} ${record.serverName} · ${record.result}`" :value="record.id" /></ElSelect></label>
        <p v-if="editorError" class="content-error wide" role="alert">{{ editorError }}</p>
      </form>
      <template #footer><ElButton @click="editorOpen = false">取消</ElButton><ElButton type="primary" :loading="saving" :disabled="!form.reportDate || !form.originalContent.trim()" @click="save">保存草稿</ElButton></template>
    </ElDialog>
  </section>
</template>

<style scoped>
.daily-filters { display: flex; flex-wrap: wrap; gap: .75rem; margin-bottom: 1rem; }
.daily-editor { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.daily-editor label { display: grid; gap: .4rem; }
.daily-editor .wide { grid-column: 1 / -1; }
@media (max-width: 640px) { .daily-editor { grid-template-columns: 1fr; } .daily-editor .wide { grid-column: auto; } }
</style>
