<script setup lang="ts">
import { onMounted,ref,watch } from 'vue'
import { ElButton,ElDatePicker,ElEmpty,ElTag,ElTimeline,ElTimelineItem } from 'element-plus'
import { ApiError } from '../api/client'; import { listProjectLifecycle,type LifecycleNode } from '../api/project-lifecycle'
const props=defineProps<{projectId:number}>(); const nodes=ref<LifecycleNode[]>([]);const range=ref<string[]>(defaultRange());const loading=ref(false);const error=ref('')
onMounted(load);watch(()=>props.projectId,load)
async function load(){if(range.value.length!==2)return;loading.value=true;try{nodes.value=await listProjectLifecycle(props.projectId,range.value[0],range.value[1]);error.value=''}catch(e){error.value=e instanceof ApiError?e.message:'加载全周期节点失败'}finally{loading.value=false}}
function defaultRange(){const now=new Date(),start=new Date(now.getFullYear(),0,1),end=new Date(now.getFullYear(),11,31);return[start.toLocaleDateString('sv-SE'),end.toLocaleDateString('sv-SE')]}
const label=(s:string)=>({NORMAL:'正常',DUE_SOON:'临期',OVERDUE:'延期'}[s]??s);const color=(s:string)=>s==='OVERDUE'?'#ef4444':s==='DUE_SOON'?'#f59e0b':'#22c55e'
</script>
<template><section class="subpanel"><div class="subpanel-heading"><div><p class="section-label">项目全貌</p><h2>全周期节点</h2><p>统一查看里程碑、部署、会议和已确认报告。</p></div></div><div class="timeline-filter"><ElDatePicker v-model="range" type="daterange" value-format="YYYY-MM-DD"/><ElButton :loading="loading" @click="load">查询</ElButton></div><p v-if="error" class="content-error">{{error}}</p><ElTimeline v-if="nodes.length" class="project-timeline"><ElTimelineItem v-for="n in nodes" :key="`${n.type}-${n.referenceId}`" :timestamp="n.date" :color="color(n.scheduleStatus)"><div class="timeline-card"><strong>{{n.title}}</strong><ElTag size="small" effect="plain">{{label(n.scheduleStatus)}}</ElTag><p>{{n.description||'无补充说明'}}</p></div></ElTimelineItem></ElTimeline><ElEmpty v-else description="所选周期暂无关键节点"/></section></template>
<style scoped>.timeline-filter{display:flex;gap:.75rem;margin-bottom:1rem}.project-timeline{padding-top:1rem}.timeline-card{display:grid;grid-template-columns:1fr auto;gap:.35rem 1rem}.timeline-card p{grid-column:1/-1;margin:0;color:#64748b;white-space:pre-wrap}</style>
