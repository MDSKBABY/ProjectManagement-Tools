<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus'

import type {
  Project,
  ProjectCreateInput,
  ProjectStatus,
  ProjectUpdateInput,
} from '../types'

const props = defineProps<{
  open: boolean
  saving: boolean
  error: string
  project: Project | null
}>()

const emit = defineEmits<{
  close: []
  submit: [input: ProjectCreateInput | ProjectUpdateInput]
}>()

interface ProjectForm {
  code: string
  name: string
  customerName: string
  status: ProjectStatus
  startDate: string
  endDate: string
  tagsText: string
  description: string
}

const form = reactive<ProjectForm>(emptyForm())
const localError = ref('')
const dateInvalid = computed(
  () => Boolean(form.startDate && form.endDate && form.endDate < form.startDate),
)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    localError.value = ''
    Object.assign(form, props.project ? fromProject(props.project) : emptyForm())
  },
)

function submit(): void {
  localError.value = ''
  if (dateInvalid.value) {
    localError.value = '项目结束日期不能早于开始日期'
    return
  }
  const common: ProjectUpdateInput = {
    name: form.name.trim(),
    customerName: form.customerName.trim() || undefined,
    status: form.status,
    startDate: form.startDate || undefined,
    endDate: form.endDate || undefined,
    tags: form.tagsText
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag, index, tags) => Boolean(tag) && tags.indexOf(tag) === index),
    description: form.description.trim() || undefined,
  }
  emit('submit', props.project ? common : { ...common, code: form.code.trim() })
}

function emptyForm(): ProjectForm {
  return {
    code: '',
    name: '',
    customerName: '',
    status: 'PLANNING',
    startDate: '',
    endDate: '',
    tagsText: '',
    description: '',
  }
}

function fromProject(project: Project): ProjectForm {
  return {
    code: project.code,
    name: project.name,
    customerName: project.customerName ?? '',
    status: project.status,
    startDate: project.startDate ?? '',
    endDate: project.endDate ?? '',
    tagsText: project.tags.join(', '),
    description: project.description ?? '',
  }
}
</script>

<template>
  <ElDialog
    :model-value="open"
    :title="project ? '编辑项目' : '创建项目'"
    width="min(42rem, calc(100vw - 2rem))"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @close="emit('close')"
  >
    <ElForm label-position="top" @submit.prevent="submit">
      <div class="form-grid">
        <ElFormItem label="项目编码" required>
          <ElInput
            v-model="form.code"
            name="project-code"
            :disabled="Boolean(project)"
            maxlength="64"
            placeholder="例如 PM-001"
          />
        </ElFormItem>
        <ElFormItem label="项目名称" required>
          <ElInput v-model="form.name" name="project-name" maxlength="200" />
        </ElFormItem>
      </div>
      <div class="form-grid">
        <ElFormItem label="客户名称">
          <ElInput v-model="form.customerName" name="customer-name" maxlength="200" />
        </ElFormItem>
        <ElFormItem label="项目状态" required>
          <ElSelect v-model="form.status" name="project-status">
            <ElOption label="规划中" value="PLANNING" />
            <ElOption label="进行中" value="ACTIVE" />
            <ElOption label="已暂停" value="PAUSED" />
            <ElOption label="已完成" value="COMPLETED" />
            <ElOption label="已归档" value="ARCHIVED" />
          </ElSelect>
        </ElFormItem>
      </div>
      <div class="form-grid">
        <ElFormItem label="计划开始日期">
          <ElInput v-model="form.startDate" name="start-date" type="date" />
        </ElFormItem>
        <ElFormItem label="计划结束日期" :error="dateInvalid ? '不能早于开始日期' : ''">
          <ElInput v-model="form.endDate" name="end-date" type="date" />
        </ElFormItem>
      </div>
      <ElFormItem label="项目标签">
        <ElInput v-model="form.tagsText" name="project-tags" maxlength="309" />
        <span class="field-help">使用英文逗号分隔，最多 10 个标签，每个不超过 30 个字符。</span>
      </ElFormItem>
      <ElFormItem label="项目说明">
        <ElInput
          v-model="form.description"
          name="project-description"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
        />
      </ElFormItem>

      <p v-if="localError || error" class="form-error" role="alert">
        {{ localError || error }}
      </p>

      <div class="dialog-actions">
        <ElButton :disabled="saving" @click="emit('close')">取消</ElButton>
        <ElButton
          type="primary"
          native-type="submit"
          :loading="saving"
          :disabled="!form.name.trim() || (!project && !form.code.trim()) || dateInvalid"
        >
          {{ project ? '保存修改' : '创建项目' }}
        </ElButton>
      </div>
    </ElForm>
  </ElDialog>
</template>
