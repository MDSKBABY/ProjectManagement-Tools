<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus'

import type { ChangePasswordInput } from '../types'

const props = defineProps<{
  open: boolean
  saving: boolean
  error: string
}>()

const emit = defineEmits<{
  close: []
  submit: [input: ChangePasswordInput]
}>()

const emptyForm = () => ({ currentPassword: '', newPassword: '', confirmation: '' })
const form = reactive(emptyForm())
const passwordsMatch = computed(() => form.newPassword === form.confirmation)
const canSubmit = computed(
  () =>
    form.currentPassword.length > 0 &&
    form.newPassword.length >= 12 &&
    form.confirmation.length >= 12 &&
    passwordsMatch.value,
)

watch(
  () => props.open,
  (open) => {
    if (open) Object.assign(form, emptyForm())
  },
)

function submit(): void {
  if (!canSubmit.value) return
  emit('submit', {
    currentPassword: form.currentPassword,
    newPassword: form.newPassword,
  })
}
</script>

<template>
  <ElDialog
    :model-value="open"
    title="修改密码"
    width="min(30rem, calc(100vw - 2rem))"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @close="emit('close')"
  >
    <p class="dialog-help">修改成功后，当前账号的所有已登录会话都会失效。</p>
    <ElForm label-position="top" @submit.prevent="submit">
      <ElFormItem label="当前密码" required>
        <ElInput
          v-model="form.currentPassword"
          name="current-password"
          type="password"
          autocomplete="current-password"
          show-password
        />
      </ElFormItem>
      <ElFormItem label="新密码" required>
        <ElInput
          v-model="form.newPassword"
          name="new-password"
          type="password"
          autocomplete="new-password"
          show-password
        />
        <span class="field-help">至少 12 个字符，UTF-8 编码后不超过 72 字节。</span>
      </ElFormItem>
      <ElFormItem label="确认新密码" required>
        <ElInput
          v-model="form.confirmation"
          name="confirm-password"
          type="password"
          autocomplete="new-password"
          show-password
        />
        <span v-if="form.confirmation && !passwordsMatch" class="field-help form-error">
          两次输入的新密码不一致。
        </span>
      </ElFormItem>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <ElButton :disabled="saving" @click="emit('close')">取消</ElButton>
        <ElButton type="primary" native-type="submit" :loading="saving" :disabled="!canSubmit">
          确认修改
        </ElButton>
      </div>
    </ElForm>
  </ElDialog>
</template>
