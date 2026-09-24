<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton } from 'element-plus'

import { changePassword, getCurrentUser, login, logout } from './api/auth'
import { ApiError } from './api/client'
import AuditLogPanel from './components/AuditLogPanel.vue'
import LoginPanel from './components/LoginPanel.vue'
import PasswordChangeDialog from './components/PasswordChangeDialog.vue'
import ProjectWorkspace from './components/ProjectWorkspace.vue'
import UserManagement from './components/UserManagement.vue'
import type { ChangePasswordInput, CurrentUser, LoginInput } from './types'

const currentUser = ref<CurrentUser | null>(null)
const initializing = ref(true)
const loginLoading = ref(false)
const loginError = ref('')
const shellError = ref('')
const passwordDialogOpen = ref(false)
const passwordSaving = ref(false)
const passwordError = ref('')
type WorkspaceSection = 'projects' | 'users' | 'audit'

const activeSection = ref<WorkspaceSection>('projects')

const canManageUsers = computed(() => currentUser.value?.permissions.includes('user:manage') ?? false)
const canViewProjects = computed(() => currentUser.value?.permissions.includes('project:read') ?? false)
const canReadAudit = computed(() => currentUser.value?.permissions.includes('audit:read') ?? false)
const visibleSectionCount = computed(
  () => Number(canViewProjects.value) + Number(canManageUsers.value) + Number(canReadAudit.value),
)

onMounted(restoreSession)

/** 刷新页面时优先恢复服务端 Session；未登录是正常状态，不展示系统错误。 */
async function restoreSession(): Promise<void> {
  try {
    currentUser.value = await getCurrentUser()
    chooseInitialSection()
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
    chooseInitialSection()
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

async function handlePasswordChange(input: ChangePasswordInput): Promise<void> {
  passwordSaving.value = true
  passwordError.value = ''
  try {
    await changePassword(input)
    passwordDialogOpen.value = false
    currentUser.value = null
  } catch (reason) {
    passwordError.value = errorMessage(reason)
  } finally {
    passwordSaving.value = false
  }
}

function openPasswordDialog(): void {
  passwordError.value = ''
  passwordDialogOpen.value = true
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}

function chooseInitialSection(): void {
  if (canViewProjects.value) activeSection.value = 'projects'
  else if (canManageUsers.value) activeSection.value = 'users'
  else activeSection.value = 'audit'
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
        <ElButton link data-test="change-password" @click="openPasswordDialog">修改密码</ElButton>
        <ElButton link data-test="logout" @click="handleLogout">退出</ElButton>
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
      <nav v-if="visibleSectionCount > 1" class="workspace-nav" aria-label="工作台导航">
        <button
          v-if="canViewProjects"
          type="button"
          :class="{ active: activeSection === 'projects' }"
          @click="activeSection = 'projects'"
        >
          项目工作台
        </button>
        <button
          v-if="canManageUsers"
          type="button"
          :class="{ active: activeSection === 'users' }"
          @click="activeSection = 'users'"
        >
          用户管理
        </button>
        <button
          v-if="canReadAudit"
          type="button"
          data-test="nav-audit"
          :class="{ active: activeSection === 'audit' }"
          @click="activeSection = 'audit'"
        >
          审计查询
        </button>
      </nav>
      <ProjectWorkspace
        v-if="activeSection === 'projects' && canViewProjects"
        :current-user="currentUser"
      />
      <UserManagement
        v-else-if="activeSection === 'users' && canManageUsers"
        :current-user="currentUser"
      />
      <AuditLogPanel v-else-if="activeSection === 'audit' && canReadAudit" />
      <section v-else class="panel permission-empty" aria-labelledby="permission-title">
        <p class="section-label">访问受限</p>
        <h1 id="permission-title">账号已登录</h1>
        <p>当前账号没有可用的工作台权限，请联系管理员授权。</p>
      </section>
    </main>

    <PasswordChangeDialog
      :open="passwordDialogOpen"
      :saving="passwordSaving"
      :error="passwordError"
      @close="passwordDialogOpen = false"
      @submit="handlePasswordChange"
    />
  </div>
</template>
