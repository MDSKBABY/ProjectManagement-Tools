<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton } from 'element-plus'

import { getCurrentUser, login, logout } from './api/auth'
import { ApiError } from './api/client'
import LoginPanel from './components/LoginPanel.vue'
import UserManagement from './components/UserManagement.vue'
import type { CurrentUser, LoginInput } from './types'

const currentUser = ref<CurrentUser | null>(null)
const initializing = ref(true)
const loginLoading = ref(false)
const loginError = ref('')
const shellError = ref('')

const canManageUsers = computed(() => currentUser.value?.permissions.includes('user:manage') ?? false)

onMounted(restoreSession)

/** 刷新页面时优先恢复服务端 Session；未登录是正常状态，不展示系统错误。 */
async function restoreSession(): Promise<void> {
  try {
    currentUser.value = await getCurrentUser()
  } catch (reason) {
    if (!(reason instanceof ApiError && reason.status === 401)) {
      shellError.value = errorMessage(reason)
    }
  } finally {
    initializing.value = false
  }
}

async function handleLogin(input: LoginInput): Promise<void> {
  loginLoading.value = true
  loginError.value = ''
  try {
    currentUser.value = await login(input)
  } catch (reason) {
    loginError.value = errorMessage(reason)
  } finally {
    loginLoading.value = false
  }
}

async function handleLogout(): Promise<void> {
  shellError.value = ''
  try {
    await logout()
    currentUser.value = null
  } catch (reason) {
    shellError.value = errorMessage(reason)
  }
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href="#main-content" aria-label="跳转到主要内容">
        <span class="brand-mark" aria-hidden="true">PM</span>
        <span>内部项目管理</span>
      </a>
      <div v-if="currentUser" class="account-area">
        <span>
          <strong>{{ currentUser.displayName }}</strong>
          <small>{{ currentUser.username }}</small>
        </span>
        <ElButton link @click="handleLogout">退出</ElButton>
      </div>
    </header>

    <main v-if="initializing" id="main-content" class="loading-page" aria-busy="true">
      <span class="loading-line" aria-hidden="true"></span>
      <p>正在恢复登录状态…</p>
    </main>

    <LoginPanel
      v-else-if="!currentUser"
      :loading="loginLoading"
      :error="loginError || shellError"
      @submit="handleLogin"
    />

    <main v-else id="main-content" class="workspace">
      <p v-if="shellError" class="content-error" role="alert">{{ shellError }}</p>
      <UserManagement v-if="canManageUsers" :current-user="currentUser" />
      <section v-else class="panel permission-empty" aria-labelledby="permission-title">
        <p class="section-label">访问受限</p>
        <h1 id="permission-title">账号已登录</h1>
        <p>当前账号没有用户管理权限。项目工作台将在下一阶段开放。</p>
      </section>
    </main>
  </div>
</template>
