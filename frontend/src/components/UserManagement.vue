<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { ApiError } from '../api/client'
import { createUser, listUsers, updateUserStatus } from '../api/users'
import type { CreateUserInput, CurrentUser, Pagination, UserStatus, UserSummary } from '../types'
import UserCreateDialog from './UserCreateDialog.vue'

const props = defineProps<{ currentUser: CurrentUser }>()

const users = ref<UserSummary[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const keyword = ref('')
const status = ref<'' | 'ACTIVE' | 'DISABLED'>('')
const loading = ref(false)
const error = ref('')
const createOpen = ref(false)
const saving = ref(false)
const createError = ref('')
const changingUserId = ref<number | null>(null)

onMounted(loadUsers)

async function loadUsers(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listUsers({
      page,
      pageSize: pagination.pageSize,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    })
    users.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function submitCreate(input: CreateUserInput): Promise<void> {
  saving.value = true
  createError.value = ''
  try {
    await createUser(input)
    createOpen.value = false
    await loadUsers(1)
  } catch (reason) {
    createError.value = errorMessage(reason)
  } finally {
    saving.value = false
  }
}

async function changeStatus(user: UserSummary): Promise<void> {
  const nextStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  changingUserId.value = user.id
  error.value = ''
  try {
    await updateUserStatus(user.id, nextStatus)
    await loadUsers()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    changingUserId.value = null
  }
}

function statusLabel(userStatus: UserStatus): string {
  return { ACTIVE: '已启用', DISABLED: '已停用', LOCKED: '已锁定' }[userStatus]
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="panel" aria-labelledby="users-title">
    <div class="panel-heading">
      <div>
        <p class="section-label">访问控制</p>
        <h1 id="users-title">用户管理</h1>
        <p>创建成员账号并管理登录状态。角色分配将在后续模块开放。</p>
      </div>
      <ElButton type="primary" @click="createOpen = true">创建用户</ElButton>
    </div>

    <form class="filter-bar" aria-label="用户筛选" @submit.prevent="loadUsers(1)">
      <ElInput v-model="keyword" clearable placeholder="搜索用户名或显示名称" aria-label="搜索用户" />
      <ElSelect v-model="status" aria-label="用户状态">
        <ElOption label="全部状态" value="" />
        <ElOption label="已启用" value="ACTIVE" />
        <ElOption label="已停用" value="DISABLED" />
      </ElSelect>
      <ElButton native-type="submit" :loading="loading">查询</ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">
      {{ error }}
      <button type="button" @click="loadUsers()">重试</button>
    </p>

    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="users" empty-text="暂无用户">
        <ElTableColumn prop="displayName" label="用户" min-width="180">
          <template #default="scope">
            <strong>{{ scope.row.displayName }}</strong>
            <span class="cell-secondary">{{ scope.row.username }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="email" label="邮箱" min-width="210">
          <template #default="scope">{{ scope.row.email || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="110">
          <template #default="scope">
            <ElTag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" align="right">
          <template #default="scope">
            <span v-if="scope.row.id === props.currentUser.id" class="cell-secondary">当前账号</span>
            <ElPopconfirm
              v-else
              :title="scope.row.status === 'ACTIVE' ? '确认停用该用户？' : '确认启用该用户？'"
              confirm-button-text="确认"
              cancel-button-text="取消"
              @confirm="changeStatus(scope.row as UserSummary)"
            >
              <template #reference>
                <ElButton link :loading="changingUserId === scope.row.id">
                  {{ scope.row.status === 'ACTIVE' ? '停用' : '启用' }}
                </ElButton>
              </template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="没有符合条件的用户" :image-size="72" />
        </template>
      </ElTable>
    </div>

    <ElPagination
      v-if="pagination.totalPages > 1"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.totalItems"
      @current-change="loadUsers"
    />

    <UserCreateDialog
      :open="createOpen"
      :saving="saving"
      :error="createError"
      @close="createOpen = false"
      @submit="submitCreate"
    />
  </section>
</template>
