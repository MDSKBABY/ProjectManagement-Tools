<script setup lang="ts">
import { reactive } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'

import type { LoginInput } from '../types'

defineProps<{
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  submit: [input: LoginInput]
}>()

const form = reactive<LoginInput>({ username: '', password: '' })

function submit(): void {
  emit('submit', { username: form.username.trim(), password: form.password })
}
</script>

<template>
  <main id="main-content" class="login-layout">
    <section class="login-intro" aria-labelledby="login-title">
      <p class="eyebrow">项目交付与实施协同</p>
      <h1 id="login-title">把项目过程留在团队里</h1>
      <p>
        集中管理项目成员、交付资料和实施记录。阶段 0 已接通安全登录与管理员用户管理。
      </p>
      <ul class="login-points" aria-label="系统能力">
        <li><span aria-hidden="true">01</span>服务端会话与权限控制</li>
        <li><span aria-hidden="true">02</span>管理员用户全生命周期</li>
        <li><span aria-hidden="true">03</span>项目能力将在下一阶段开放</li>
      </ul>
    </section>

    <section class="login-card" aria-labelledby="form-title">
      <div class="login-card__heading">
        <p class="section-label">安全登录</p>
        <h2 id="form-title">进入管理工作台</h2>
        <p>使用管理员初始化时设置的账号密码。</p>
      </div>

      <ElForm label-position="top" @submit.prevent="submit">
        <ElFormItem label="用户名" required>
          <ElInput
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="例如：admin"
            :disabled="loading"
          />
        </ElFormItem>
        <ElFormItem label="密码" required>
          <ElInput
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="请输入密码"
            :disabled="loading"
          />
        </ElFormItem>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <ElButton
          class="login-button"
          type="primary"
          native-type="submit"
          :loading="loading"
          :disabled="!form.username.trim() || !form.password"
        >
          登录
        </ElButton>
      </ElForm>
    </section>
  </main>
</template>
