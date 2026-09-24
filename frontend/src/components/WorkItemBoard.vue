<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElTag } from 'element-plus'

import { ApiError } from '../api/client'
import { listWorkItems } from '../api/work-items'
import type { WorkItem, WorkItemPriority, WorkItemStatus } from '../types'

const props = defineProps<{
  projectId: number
  canTransition: boolean
  currentUserId?: number
  canManage?: boolean
  refreshKey?: number
}>()
const emit = defineEmits<{
  select: [item: WorkItem]
  transition: [item: WorkItem]
}>()

const items = ref<WorkItem[]>([])
const loading = ref(false)
const error = ref('')
const columns: Array<{ status: WorkItemStatus; label: string }> = [
  { status: 'TODO', label: '待处理' },
  { status: 'IN_PROGRESS', label: '进行中' },
  { status: 'DONE', label: '已完成' },
  { status: 'CANCELED', label: '已取消' },
]
const groupedItems = computed(() => Object.fromEntries(
  columns.map(({ status }) => [status, items.value.filter((item) => item.status === status)]),
) as Record<WorkItemStatus, WorkItem[]>)

onMounted(load)
watch(() => [props.projectId, props.refreshKey], load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    items.value = (await listWorkItems(props.projectId, { page: 1, pageSize: 100 })).data
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '看板加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function priorityLabel(priority: WorkItemPriority): string {
  return { LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' }[priority]
}
</script>

<template>
  <div class="work-item-view" :aria-busy="loading">
    <p v-if="error" class="content-error" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </p>
    <div class="work-item-board">
      <section v-for="column in columns" :key="column.status" class="board-column">
        <header>
          <h3>{{ column.label }}</h3>
          <span>{{ groupedItems[column.status].length }}</span>
        </header>
        <div class="board-card-list">
          <article
            v-for="item in groupedItems[column.status]"
            :key="item.id"
            class="board-card"
            :data-test="`board-item-${item.id}`"
            tabindex="0"
            @click="emit('select', item)"
            @keydown.enter="emit('select', item)"
          >
            <div class="board-card-heading">
              <ElTag size="small" effect="plain">{{ item.type === 'TASK' ? '任务' : '里程碑' }}</ElTag>
              <span>#{{ item.id }}</span>
            </div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.assignee?.displayName || '未指派' }} · {{ priorityLabel(item.priority) }}</p>
            <ElButton
              v-if="canTransition && (canManage || item.assignee?.id === currentUserId)"
              link
              type="primary"
              @click.stop="emit('transition', item)"
            >
              流转状态
            </ElButton>
          </article>
          <ElEmpty v-if="!groupedItems[column.status].length" description="暂无工作项" :image-size="44" />
        </div>
      </section>
    </div>
  </div>
</template>
