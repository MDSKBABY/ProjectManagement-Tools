<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElInput, ElOption, ElSelect } from 'element-plus'

import type { FileAsset, KnowledgeArticle, SaveKnowledgeArticleInput } from '../types'

const props = defineProps<{
  open: boolean
  article: KnowledgeArticle | null
  files: FileAsset[]
  saving: boolean
  error: string
}>()
const emit = defineEmits<{
  close: []
  submit: [input: SaveKnowledgeArticleInput]
}>()

const form = reactive<SaveKnowledgeArticleInput>({
  title: '', scenario: '', symptom: '', cause: '', solution: '',
  applicableConditions: '', tags: [], attachmentIds: [],
})
const tagText = ref('')
const ready = computed(() => Boolean(
  form.title.trim() && form.scenario.trim() && form.symptom.trim()
  && form.cause.trim() && form.solution.trim(),
))

watch(
  () => [props.open, props.article] as const,
  () => {
    if (!props.open) return
    const article = props.article
    Object.assign(form, {
      title: article?.title ?? '',
      scenario: article?.scenario ?? '',
      symptom: article?.symptom ?? '',
      cause: article?.cause ?? '',
      solution: article?.solution ?? '',
      applicableConditions: article?.applicableConditions ?? '',
      tags: article?.tags ?? [],
      attachmentIds: article?.attachments.map(({ id }) => id) ?? [],
    })
    tagText.value = form.tags.join(', ')
  },
  { immediate: true },
)

function submit(): void {
  if (!ready.value) return
  emit('submit', {
    ...form,
    title: form.title.trim(),
    scenario: form.scenario.trim(),
    symptom: form.symptom.trim(),
    cause: form.cause.trim(),
    solution: form.solution.trim(),
    applicableConditions: form.applicableConditions?.trim() || undefined,
    tags: [...new Set(tagText.value.split(',').map((tag) => tag.trim()).filter(Boolean))].slice(0, 10),
  })
}
</script>

<template>
  <ElDialog
    :model-value="open"
    :title="article ? '修订技术知识' : '新建技术知识'"
    width="min(760px, 94vw)"
    destroy-on-close
    @close="emit('close')"
  >
    <form class="knowledge-editor" aria-label="技术知识表单" @submit.prevent="submit">
      <label><span>标题</span><ElInput v-model="form.title" maxlength="200" show-word-limit /></label>
      <label><span>适用场景</span><ElInput v-model="form.scenario" maxlength="500" /></label>
      <label><span>问题现象</span><ElInput v-model="form.symptom" type="textarea" :rows="3" maxlength="10000" /></label>
      <label><span>问题原因</span><ElInput v-model="form.cause" type="textarea" :rows="3" maxlength="10000" /></label>
      <label><span>解决办法</span><ElInput v-model="form.solution" type="textarea" :rows="4" maxlength="20000" /></label>
      <label><span>适用条件</span><ElInput v-model="form.applicableConditions" type="textarea" :rows="2" maxlength="5000" /></label>
      <label><span>标签</span><ElInput v-model="tagText" placeholder="使用英文逗号分隔，最多 10 个" /></label>
      <label>
        <span>附件</span>
        <ElSelect v-model="form.attachmentIds" multiple filterable placeholder="选择已上传完成的项目文件">
          <ElOption
            v-for="file in files"
            :key="file.id"
            :label="`${file.originalName} · v${file.version}`"
            :value="file.id"
          />
        </ElSelect>
        <small>草稿可暂不选择；提交审核前至少需要一个附件。</small>
      </label>
      <p v-if="error" class="content-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <ElButton @click="emit('close')">取消</ElButton>
      <ElButton type="primary" :disabled="!ready" :loading="saving" @click="submit">保存草稿</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.knowledge-editor {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.knowledge-editor label {
  display: grid;
  gap: 0.375rem;
}

.knowledge-editor label:nth-child(3),
.knowledge-editor label:nth-child(4),
.knowledge-editor label:nth-child(5),
.knowledge-editor label:nth-child(6),
.knowledge-editor label:nth-child(8),
.knowledge-editor .content-error {
  grid-column: 1 / -1;
}

.knowledge-editor small { color: #64748b; }

@media (max-width: 640px) {
  .knowledge-editor { grid-template-columns: 1fr; }
  .knowledge-editor label:nth-child(n),
  .knowledge-editor .content-error { grid-column: auto; }
}
</style>
