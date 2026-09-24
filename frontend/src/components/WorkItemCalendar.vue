<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElTag } from 'element-plus'

import { ApiError } from '../api/client'
import { listWorkItems } from '../api/work-items'
import type { WorkItem } from '../types'

const props = defineProps<{ projectId: number; refreshKey?: number }>()
const emit = defineEmits<{ select: [item: WorkItem] }>()

const visibleMonth = ref(startOfMonth(new Date()))
const items = ref<WorkItem[]>([])
const loading = ref(false)
const error = ref('')
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const monthLabel = computed(() => `${visibleMonth.value.getFullYear()} 年 ${visibleMonth.value.getMonth() + 1} 月`)
const days = computed(() => buildCalendarDays(visibleMonth.value))

onMounted(load)
watch(() => [props.projectId, props.refreshKey], load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  const first = startOfMonth(visibleMonth.value)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
  try {
    items.value = (await listWorkItems(props.projectId, {
      page: 1,
      pageSize: 100,
      plannedFrom: formatDate(first),
      plannedTo: formatDate(last),
    })).data
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '日历加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function changeMonth(offset: number): Promise<void> {
  visibleMonth.value = new Date(
    visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + offset, 1,
  )
  await load()
}

function itemsFor(date: Date): WorkItem[] {
  const value = formatDate(date)
  return items.value.filter((item) => {
    const start = item.plannedStartDate ?? item.plannedEndDate
    const end = item.plannedEndDate ?? item.plannedStartDate
    if (!start || !end) return false
    return start <= value && end >= value
  })
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function buildCalendarDays(month: Date): Date[] {
  const first = startOfMonth(month)
  const mondayOffset = (first.getDay() + 6) % 7
  const gridStart = new Date(first.getFullYear(), first.getMonth(), 1 - mondayOffset)
  return Array.from({ length: 42 }, (_, index) => new Date(
    gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + index,
  ))
}
</script>

<template>
  <div class="work-item-view" :aria-busy="loading">
    <div class="calendar-toolbar">
      <ElButton aria-label="上个月" @click="changeMonth(-1)">上个月</ElButton>
      <h3>{{ monthLabel }}</h3>
      <ElButton aria-label="下个月" @click="changeMonth(1)">下个月</ElButton>
    </div>
    <p v-if="error" class="content-error" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </p>
    <div class="work-item-calendar">
      <div v-for="weekday in weekdays" :key="weekday" class="calendar-weekday">周{{ weekday }}</div>
      <div
        v-for="day in days"
        :key="formatDate(day)"
        class="calendar-day"
        :class="{ muted: day.getMonth() !== visibleMonth.getMonth() }"
      >
        <span>{{ day.getDate() }}</span>
        <button
          v-for="item in itemsFor(day)"
          :key="item.id"
          type="button"
          class="calendar-item"
          @click="emit('select', item)"
        >
          <ElTag size="small" effect="plain">{{ item.type === 'MILESTONE' ? '◆' : '·' }}</ElTag>
          {{ item.title }}
        </button>
      </div>
    </div>
  </div>
</template>
