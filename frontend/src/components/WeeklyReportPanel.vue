<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElButton, ElDatePicker, ElDialog, ElEmpty, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ApiError } from '../api/client'
import { confirmWeeklyReport, generateWeeklyReport, listWeeklyReports, updateWeeklyReport } from '../api/weekly-reports'
import type { WeeklyReport } from '../types'

const props = defineProps<{ projectId: number; canWrite: boolean; canConfirm: boolean }>()
const reports = ref<WeeklyReport[]>([])
const loading = ref(false); const saving = ref(false); const error = ref('')
const open = ref(false); const editing = ref<WeeklyReport | null>(null)
const form = reactive({ period: [] as string[], content: '', nextWeekPlan: '' })
onMounted(load); watch(() => props.projectId, load)
async function load() { loading.value=true; error.value=''; try { reports.value=await listWeeklyReports(props.projectId) } catch(e){ error.value=message(e) } finally { loading.value=false } }
function startGenerate(){ editing.value=null; Object.assign(form,{period:[],content:'',nextWeekPlan:''}); open.value=true }
function startEdit(r:WeeklyReport|any){ editing.value=r; Object.assign(form,{period:[r.periodStart,r.periodEnd],content:r.content,nextWeekPlan:r.nextWeekPlan??''}); open.value=true }
async function save(){ saving.value=true; error.value=''; try { if(editing.value) await updateWeeklyReport(props.projectId,editing.value.id,{content:form.content,nextWeekPlan:form.nextWeekPlan||undefined}); else await generateWeeklyReport(props.projectId,{periodStart:form.period[0],periodEnd:form.period[1],nextWeekPlan:form.nextWeekPlan||undefined}); open.value=false; await load() } catch(e){ error.value=message(e) } finally {saving.value=false} }
async function confirm(r:WeeklyReport|any){ saving.value=true; try{await confirmWeeklyReport(props.projectId,r.id);await load()}catch(e){error.value=message(e)}finally{saving.value=false} }
function message(e:unknown){return e instanceof ApiError?e.message:'周报操作失败，请稍后重试'}
</script>
<template>
  <section class="subpanel" aria-labelledby="weekly-title">
    <div class="subpanel-heading"><div><p class="section-label">阶段汇总</p><h2 id="weekly-title">项目周报</h2><p>基于已确认日报生成，并保留生成时快照。</p></div><ElButton v-if="canWrite" type="primary" data-test="generate-weekly-report" @click="startGenerate">生成周报</ElButton></div>
    <p v-if="error" class="content-error">{{error}}</p>
    <ElTable :data="reports" :aria-busy="loading">
      <ElTableColumn label="周期" min-width="190"><template #default="s">{{s.row.periodStart}} 至 {{s.row.periodEnd}}</template></ElTableColumn>
      <ElTableColumn label="本周总结" prop="content" min-width="300" show-overflow-tooltip />
      <ElTableColumn label="日报快照" width="100"><template #default="s">{{s.row.dailySnapshots.length}} 条</template></ElTableColumn>
      <ElTableColumn label="状态" width="100"><template #default="s"><ElTag :type="s.row.status==='CONFIRMED'?'success':'info'">{{s.row.status==='CONFIRMED'?'已确认':'草稿'}}</ElTag></template></ElTableColumn>
      <ElTableColumn label="操作" width="150"><template #default="s"><template v-if="s.row.status==='DRAFT'"><ElButton v-if="canWrite" link @click="startEdit(s.row)">编辑</ElButton><ElButton v-if="canConfirm" link type="success" @click="confirm(s.row)">确认</ElButton></template></template></ElTableColumn>
      <template #empty><ElEmpty description="暂无周报" :image-size="64" /></template>
    </ElTable>
    <ElDialog :model-value="open" :title="editing?'编辑周报草稿':'生成周报'" width="min(760px,94vw)" @close="open=false">
      <form class="weekly-form" @submit.prevent="save"><label v-if="!editing"><span>周报周期</span><ElDatePicker v-model="form.period" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" /></label><label v-if="editing"><span>本周总结</span><ElInput v-model="form.content" type="textarea" :rows="8" /></label><label><span>下周计划</span><ElInput v-model="form.nextWeekPlan" type="textarea" :rows="4" /></label></form>
      <template #footer><ElButton @click="open=false">取消</ElButton><ElButton type="primary" :disabled="editing?!form.content.trim():form.period.length!==2" :loading="saving" @click="save">{{editing?'保存草稿':'生成快照'}}</ElButton></template>
    </ElDialog>
  </section>
</template>
<style scoped>.weekly-form{display:grid;gap:1rem}.weekly-form label{display:grid;gap:.4rem}</style>
