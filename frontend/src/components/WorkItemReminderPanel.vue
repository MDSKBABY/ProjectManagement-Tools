<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElInput,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import {
  createWorkItemReminder,
  deleteWorkItemReminder,
  dismissWorkItemReminder,
  listWorkItemReminders,
  listWorkItems,
} from '../api/work-items'
import type { WorkItem, WorkItemReminder } from '../types'

const props = defineProps<{
  projectId: number
  canWrite: boolean
  selectedWorkItemId?: number
}>()

const reminders = ref<WorkItemReminder[]>([])
const workItems = ref<WorkItem[]>([])
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const form = reactive({ workItemId: null as number | null, remindAt: null as Date | null, message: '' })
const formReady = computed(() => Boolean(
  form.workItemId && form.remindAt && form.remindAt.getTime() > Date.now(),
))

onMounted(load)
watch(() => props.projectId, load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [reminderPage, itemPage] = await Promise.all([
      listWorkItemReminders(props.projectId, { page: 1, pageSize: 100 }),
      listWorkItems(props.projectId, { page: 1, pageSize: 100 }),
    ])
    reminders.value = reminderPage.data
    workItems.value = itemPage.data
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.workItemId = props.selectedWorkItemId ?? null
  form.remindAt = null
  form.message = ''
  error.value = ''
  dialogOpen.value = true
}

async function save(): Promise<void> {
  if (!formReady.value || !form.workItemId || !form.remindAt) return
  saving.value = true
  error.value = ''
  try {
    await createWorkItemReminder(props.projectId, {
      workItemId: form.workItemId,
      remindAt: form.remindAt.toISOString(),
      message: form.message.trim() || undefined,
    })
    dialogOpen.value = false
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function dismiss(reminderId: number): Promise<void> {
  try {
    await dismissWorkItemReminder(props.projectId, reminderId)
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

async function remove(reminderId: number): Promise<void> {
  try {
    await deleteWorkItemReminder(props.projectId, reminderId)
    await load()
  } catch (reason) {
    error.value = errorMessage(reason)
  }
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '提醒操作失败，请稍后重试'
}
</script>

<template>
  <section class="work-item-reminders" :aria-busy="loading" aria-labelledby="reminder-title">
    <div class="trace-section-heading">
      <div>
        <h3 id="reminder-title">我的提醒</h3>
        <p>提醒仅自己可见，到点后可在这里查看并关闭。</p>
      </div>
      <ElButton v-if="canWrite" data-test="create-reminder" type="primary" plain @click="openCreate">
        新建提醒
      </ElButton>
    </div>
    <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    <ul v-if="reminders.length" class="reminder-list">
      <li v-for="reminder in reminders" :key="reminder.id">
        <div>
          <div class="reminder-heading">
            <strong>{{ reminder.workItem.title }}</strong>
            <ElTag :type="reminder.status === 'PENDING' ? 'warning' : 'info'" effect="plain" size="small">
              {{ reminder.status === 'PENDING' ? '待提醒' : '已关闭' }}
            </ElTag>
          </div>
          <p>{{ reminder.message || '工作项提醒' }}</p>
          <small>{{ formatDateTime(reminder.remindAt) }}</small>
        </div>
        <div class="reminder-actions">
          <ElButton
            v-if="canWrite && reminder.status === 'PENDING'"
            :data-test="`dismiss-reminder-${reminder.id}`"
            link
            type="primary"
            @click="dismiss(reminder.id)"
          >关闭</ElButton>
          <ElPopconfirm
            v-if="canWrite"
            title="确认删除该提醒？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="remove(reminder.id)"
          >
            <template #reference><ElButton link type="danger">删除</ElButton></template>
          </ElPopconfirm>
        </div>
      </li>
    </ul>
    <ElEmpty v-else description="暂无个人提醒" :image-size="54" />

    <ElDialog v-model="dialogOpen" title="新建工作项提醒" width="min(32rem, 94vw)" destroy-on-close>
      <div class="dialog-form-stack">
        <label>
          <span>工作项</span>
          <ElSelect v-model="form.workItemId" filterable placeholder="选择工作项">
            <ElOption v-for="item in workItems" :key="item.id" :label="`${item.title} (#${item.id})`" :value="item.id" />
          </ElSelect>
        </label>
        <label><span>提醒时间</span><ElDatePicker v-model="form.remindAt" type="datetime" placeholder="选择未来时间" /></label>
        <label><span>提醒内容</span><ElInput v-model="form.message" maxlength="500" show-word-limit placeholder="可选" /></label>
        <p v-if="form.remindAt && form.remindAt.getTime() <= Date.now()" class="field-error">提醒时间必须晚于当前时间。</p>
      </div>
      <template #footer>
        <ElButton @click="dialogOpen = false">取消</ElButton>
        <ElButton type="primary" :disabled="!formReady" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
