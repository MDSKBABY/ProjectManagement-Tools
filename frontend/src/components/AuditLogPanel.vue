<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { listAuditLogs } from '../api/audit-logs'
import { ApiError } from '../api/client'
import type { AuditLogEntry, AuditOutcome, Pagination } from '../types'

const entries = ref<AuditLogEntry[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const action = ref('')
const resourceType = ref('')
const outcome = ref<'' | AuditOutcome>('')
const createdFrom = ref('')
const createdTo = ref('')
const loading = ref(false)
const error = ref('')

onMounted(() => loadAuditLogs(1))

async function loadAuditLogs(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listAuditLogs({
      page,
      pageSize: pagination.pageSize,
      action: normalizeCode(action.value),
      resourceType: normalizeCode(resourceType.value),
      outcome: outcome.value || undefined,
      createdFrom: toInstant(createdFrom.value),
      createdTo: toInstant(createdTo.value),
    })
    entries.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '审计日志加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function normalizeCode(value: string): string | undefined {
  return value.trim() ? value.trim().toUpperCase() : undefined
}

function toInstant(value: string): string | undefined {
  return value ? new Date(value).toISOString() : undefined
}

function formatDetails(details: Record<string, unknown>): string {
  return JSON.stringify(details, null, 2)
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}
</script>

<template>
  <section class="panel" aria-labelledby="audit-title">
    <div class="panel-heading">
      <div>
        <p class="section-label">运维与合规</p>
        <h1 id="audit-title">审计查询</h1>
        <p>按操作、资源和时间追溯系统中的关键变更。</p>
      </div>
    </div>

    <form class="audit-filter" aria-label="审计筛选" @submit.prevent="loadAuditLogs(1)">
      <ElInput v-model="action" clearable placeholder="例如 PROJECT_CREATED" aria-label="审计动作" />
      <ElInput v-model="resourceType" clearable placeholder="例如 PROJECT" aria-label="资源类型" />
      <ElSelect v-model="outcome" aria-label="执行结果">
        <ElOption label="全部结果" value="" />
        <ElOption label="成功" value="SUCCESS" />
        <ElOption label="失败" value="FAILURE" />
      </ElSelect>
      <label class="compact-field">
        <span>开始时间</span>
        <input v-model="createdFrom" class="native-field" type="datetime-local" />
      </label>
      <label class="compact-field">
        <span>结束时间</span>
        <input v-model="createdTo" class="native-field" type="datetime-local" />
      </label>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">
      {{ error }}
      <button type="button" @click="loadAuditLogs()">重试</button>
    </p>

    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="entries" empty-text="暂无审计日志">
        <ElTableColumn label="时间" min-width="180">
          <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作人" min-width="150">
          <template #default="scope">
            <strong>{{ scope.row.actor?.displayName || '系统' }}</strong>
            <span v-if="scope.row.actor" class="cell-secondary">{{ scope.row.actor.username }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="action" label="动作" min-width="220" />
        <ElTableColumn label="资源" min-width="180">
          <template #default="scope">
            {{ scope.row.resourceType }}
            <span class="cell-secondary">#{{ scope.row.resourceId || '—' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="结果" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.outcome === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
              {{ scope.row.outcome === 'SUCCESS' ? '成功' : '失败' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="追踪信息" min-width="260">
          <template #default="scope">
            <span class="cell-secondary">{{ scope.row.requestId || '无请求标识' }}</span>
            <details class="audit-details">
              <summary>查看详情</summary>
              <pre>{{ formatDetails(scope.row.details) }}</pre>
            </details>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="没有符合条件的审计日志" :image-size="72" />
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
      @current-change="loadAuditLogs"
    />
  </section>
</template>
