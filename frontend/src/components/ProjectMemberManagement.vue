<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
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
import {
  addProjectMember,
  listProjectMemberCandidates,
  listProjectMembers,
  removeProjectMember,
  updateProjectMemberRole,
} from '../api/projects'
import type {
  EditableProjectMemberRole,
  Pagination,
  ProjectMember,
  ProjectMemberCandidate,
} from '../types'

const props = defineProps<{ projectId: number; canManage: boolean }>()

const members = ref<ProjectMember[]>([])
const pagination = reactive<Pagination>({ page: 1, pageSize: 20, totalItems: 0, totalPages: 0 })
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const changingUserId = ref<number | null>(null)

const candidateKeyword = ref('')
const candidates = ref<ProjectMemberCandidate[]>([])
const candidateUserId = ref<number | null>(null)
const candidateRole = ref<EditableProjectMemberRole>('MEMBER')
const candidateLoading = ref(false)
const adding = ref(false)

onMounted(() => loadMembers(1))
watch(
  () => props.projectId,
  () => {
    keyword.value = ''
    candidates.value = []
    candidateUserId.value = null
    void loadMembers(1)
  },
)

async function loadMembers(page = pagination.page): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listProjectMembers(props.projectId, {
      page,
      pageSize: pagination.pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    members.value = result.data
    Object.assign(pagination, result.pagination)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

async function searchCandidates(): Promise<void> {
  candidateLoading.value = true
  error.value = ''
  try {
    const result = await listProjectMemberCandidates(props.projectId, {
      page: 1,
      pageSize: 20,
      keyword: candidateKeyword.value.trim() || undefined,
    })
    candidates.value = result.data
    candidateUserId.value = result.data[0]?.userId ?? null
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    candidateLoading.value = false
  }
}

async function addSelectedCandidate(): Promise<void> {
  if (!candidateUserId.value) return
  adding.value = true
  error.value = ''
  try {
    await addProjectMember(props.projectId, {
      userId: candidateUserId.value,
      role: candidateRole.value,
    })
    candidates.value = candidates.value.filter(({ userId }) => userId !== candidateUserId.value)
    candidateUserId.value = null
    await loadMembers(1)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    adding.value = false
  }
}

async function changeRole(member: ProjectMember, role: EditableProjectMemberRole): Promise<void> {
  changingUserId.value = member.userId
  error.value = ''
  try {
    await updateProjectMemberRole(props.projectId, member.userId, role)
    await loadMembers()
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    changingUserId.value = null
  }
}

async function removeMember(member: ProjectMember): Promise<void> {
  changingUserId.value = member.userId
  error.value = ''
  try {
    await removeProjectMember(props.projectId, member.userId)
    await loadMembers(1)
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    changingUserId.value = null
  }
}

function errorMessage(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : '请求失败，请稍后重试'
}
</script>

<template>
  <section class="member-panel" aria-labelledby="project-members-title">
    <div class="member-heading">
      <div>
        <p class="section-label">协作成员</p>
        <h2 id="project-members-title">项目成员</h2>
      </div>
      <form class="member-search" aria-label="成员搜索" @submit.prevent="loadMembers(1)">
        <ElInput v-model="keyword" clearable placeholder="搜索当前成员" aria-label="搜索当前成员" />
        <ElButton native-type="submit" :loading="loading">查询</ElButton>
      </form>
    </div>

    <form
      v-if="canManage"
      class="candidate-bar"
      aria-label="添加项目成员"
      @submit.prevent="addSelectedCandidate"
    >
      <ElInput
        v-model="candidateKeyword"
        data-test="candidate-search"
        placeholder="输入用户名或姓名"
        aria-label="搜索可添加用户"
      />
      <ElButton :loading="candidateLoading" @click="searchCandidates">搜索用户</ElButton>
      <ElSelect v-model="candidateUserId" placeholder="选择用户" aria-label="选择用户">
        <ElOption
          v-for="candidate in candidates"
          :key="candidate.userId"
          :label="`${candidate.displayName}（${candidate.username}）`"
          :value="candidate.userId"
        />
      </ElSelect>
      <ElSelect v-model="candidateRole" aria-label="项目角色">
        <ElOption label="管理员" value="MANAGER" />
        <ElOption label="成员" value="MEMBER" />
        <ElOption label="访客" value="VIEWER" />
      </ElSelect>
      <ElButton native-type="submit" type="primary" :disabled="!candidateUserId" :loading="adding">
        添加
      </ElButton>
    </form>

    <p v-if="error" class="content-error" role="alert">{{ error }}</p>

    <div class="table-scroll" :aria-busy="loading">
      <ElTable :data="members" empty-text="暂无项目成员">
        <ElTableColumn label="成员" min-width="180">
          <template #default="scope">
            <strong>{{ scope.row.displayName }}</strong>
            <span class="cell-secondary">{{ scope.row.username }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="账号状态" width="110">
          <template #default="scope">
            <ElTag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ scope.row.status === 'ACTIVE' ? '已启用' : '不可用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="项目角色" min-width="170">
          <template #default="scope">
            <strong v-if="scope.row.role === 'OWNER'">OWNER</strong>
            <ElSelect
              v-else-if="canManage"
              :model-value="scope.row.role"
              :loading="changingUserId === scope.row.userId"
              aria-label="调整项目角色"
              @change="changeRole(scope.row as ProjectMember, $event as EditableProjectMemberRole)"
            >
              <ElOption label="管理员" value="MANAGER" />
              <ElOption label="成员" value="MEMBER" />
              <ElOption label="访客" value="VIEWER" />
            </ElSelect>
            <span v-else>{{ scope.row.role }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="canManage" label="操作" width="90" align="right">
          <template #default="scope">
            <span v-if="scope.row.role === 'OWNER'" class="cell-secondary">受保护</span>
            <ElPopconfirm
              v-else
              title="确认移除该项目成员？"
              confirm-button-text="确认"
              cancel-button-text="取消"
              @confirm="removeMember(scope.row as ProjectMember)"
            >
              <template #reference><ElButton link>移除</ElButton></template>
            </ElPopconfirm>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="没有符合条件的项目成员" :image-size="64" /></template>
      </ElTable>
    </div>

    <ElPagination
      v-if="pagination.totalPages > 1"
      class="pagination"
      layout="prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.pageSize"
      :total="pagination.totalItems"
      @current-change="loadMembers"
    />
  </section>
</template>
