<script setup lang="ts">
import { reactive, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus'

import type { CreateUserInput } from '../types'

const props = defineProps<{
  open: boolean
  saving: boolean
  error: string
}>()

const emit = defineEmits<{
  close: []
  submit: [input: CreateUserInput]
}>()

const emptyForm = (): CreateUserInput => ({
  username: '',
  initialPassword: '',
  displayName: '',
  email: '',
  mobile: '',
})
const form = reactive<CreateUserInput>(emptyForm())

watch(
  () => props.open,
  (open) => {
    if (open) Object.assign(form, emptyForm())
  },
)

function submit(): void {
  emit('submit', {
    username: form.username.trim(),
    initialPassword: form.initialPassword,
    displayName: form.displayName.trim(),
    email: form.email?.trim() || undefined,
    mobile: form.mobile?.trim() || undefined,
  })
}
</script>

<template>
  <ElDialog
    :model-value="open"
    title="创建用户"
    width="min(32rem, calc(100vw - 2rem))"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @close="emit('close')"
  >
    <p class="dialog-help">新用户会自动获得最低权限的 VISITOR 角色。</p>
    <ElForm label-position="top" @submit.prevent="submit">
      <div class="form-grid">
        <ElFormItem label="用户名" required>
          <ElInput v-model="form.username" name="new-username" autocomplete="off" />
        </ElFormItem>
        <ElFormItem label="显示名称" required>
          <ElInput v-model="form.displayName" name="display-name" autocomplete="name" />
        </ElFormItem>
      </div>
      <ElFormItem label="初始密码" required>
        <ElInput
          v-model="form.initialPassword"
          name="new-password"
          type="password"
          autocomplete="new-password"
          show-password
        />
        <span class="field-help">至少 12 个字符，UTF-8 编码后不超过 72 字节。</span>
      </ElFormItem>
      <div class="form-grid">
        <ElFormItem label="邮箱">
          <ElInput v-model="form.email" name="email" type="email" autocomplete="email" />
        </ElFormItem>
        <ElFormItem label="手机号">
          <ElInput v-model="form.mobile" name="mobile" autocomplete="tel" />
        </ElFormItem>
      </div>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <div class="dialog-actions">
        <ElButton :disabled="saving" @click="emit('close')">取消</ElButton>
        <ElButton
          type="primary"
          native-type="submit"
          :loading="saving"
          :disabled="!form.username.trim() || !form.displayName.trim() || form.initialPassword.length < 12"
        >
          创建用户
        </ElButton>
      </div>
    </ElForm>
  </ElDialog>
</template>
