<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus'

import type { UserSummary } from '../types'

const props = defineProps<{
  open: boolean
  saving: boolean
  error: string
  user: UserSummary | null
}>()

const emit = defineEmits<{
  close: []
  submit: [newPassword: string]
}>()

const form = reactive({ newPassword: '', confirmation: '' })
const passwordsMatch = computed(() => form.newPassword === form.confirmation)
const canSubmit = computed(
  () => form.newPassword.length >= 12 && form.confirmation.length >= 12 && passwordsMatch.value,
)

watch(
  () => props.open,
  (open) => {
    if (open) Object.assign(form, { newPassword: '', confirmation: '' })
  },
)

function submit(): void {
  if (canSubmit.value) emit('submit', form.newPassword)
}
</script>

<template>
  <ElDialog
    :model-value="open"
    title="重置用户密码"
    width="min(30rem, calc(100vw - 2rem))"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    @close="emit('close')"
  >
    <p class="dialog-help">
      正在为 <strong>{{ user?.displayName }}</strong> ({{ user?.username }}) 设置新密码。成功后该用户的现有会话会失效。
    </p>
    <ElForm label-position="top" @submit.prevent="submit">
      <ElFormItem label="新密码" required>
        <ElInput
          v-model="form.newPassword"
          name="reset-password"
          type="password"
          autocomplete="new-password"
          show-password
        />
        <span class="field-help">至少 12 个字符，UTF-8 编码后不超过 72 字节。</span>
      </ElFormItem>
      <ElFormItem label="确认新密码" required>
        <ElInput
          v-model="form.confirmation"
          name="reset-password-confirmation"
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
          确认重置
        </ElButton>
      </div>
    </ElForm>
  </ElDialog>
</template>
