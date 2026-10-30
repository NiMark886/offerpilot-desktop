<script setup lang="ts">
import BrandBlock from '@/components/BrandBlock.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  adminQuestionApi,
  adminTrackApi,
  type AdminQuestion,
  type AdminQuestionRequest,
  type AdminTrack,
  type QuestionBankOptions,
  type QuestionDocumentExtract,
  type QuestionImportDefaults,
  type QuestionImportItem
} from '@/api/interview'
import { useAuthStore } from '@/stores/auth'

interface ImportRow extends QuestionImportItem {
  id: number
  selected: boolean
}

const auth = useAuthStore()
const router = useRouter()

const loading = ref(false)
const records = ref<AdminQuestion[]>([])
const tracks = ref<AdminTrack[]>([])
const activeTrackId = ref<number | null>(null)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const dialogVisible = ref(false)
const formTags = ref<string[]>([])

const questionOptions = reactive<QuestionBankOptions>({
  categories: [],
  tags: [],
  sources: []
})

const importVisible = ref(false)
const extracting = ref(false)
const importing = ref(false)
const extractResult = ref<QuestionDocumentExtract | null>(null)
const importRows = ref<ImportRow[]>([])
const importTrackIds = ref<number[]>([])
const rowDialogVisible = ref(false)
const editingRow = ref<ImportRow | null>(null)

const importDefaults = reactive<QuestionImportDefaults>({
  category: 'Java 基础',
  difficulty: 1,
  source: '文档导入'
})

const rowForm = reactive<QuestionImportItem>({
  question: '',
  referenceAnswer: '',
  category: 'Java 基础',
  difficulty: 1,
  tags: [],
  source: '文档导入'
})

const form = reactive<AdminQuestionRequest>({
  category: 'Java 基础',
  tags: '',
  difficulty: 1,
  question: '',
  referenceAnswer: '',
  source: '个人整理',
  status: 'ACTIVE',
  trackIds: []
})

const difficultyLabels: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }
const difficultyChoices = [
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' }
]

const selectedImportRows = computed(() => importRows.value.filter((row) => row.selected))
const selectedCount = computed(() => selectedImportRows.value.length)
const allSelected = computed(
  () => importRows.value.length > 0 && selectedCount.value === importRows.value.length
)
const someSelected = computed(() => selectedCount.value > 0 && !allSelected.value)

function withCurrent(options: string[], current?: string): string[] {
  const value = current?.trim()
  return value && !options.includes(value) ? [value, ...options] : options
}

const categoryChoices = computed(() => withCurrent(questionOptions.categories, form.category))
const sourceChoices = computed(() => withCurrent(questionOptions.sources, form.source))
const formTagChoices = computed(() =>
  Array.from(new Set([...formTags.value, ...questionOptions.tags]))
)
const importCategoryChoices = computed(() =>
  withCurrent(questionOptions.categories, importDefaults.category)
)
const importSourceChoices = computed(() =>
  withCurrent(questionOptions.sources, importDefaults.source)
)
const rowCategoryChoices = computed(() => withCurrent(questionOptions.categories, rowForm.category))
const rowSourceChoices = computed(() => withCurrent(questionOptions.sources, rowForm.source))
const rowTagChoices = computed(() =>
  Array.from(new Set([...rowForm.tags, ...questionOptions.tags]))
)

onMounted(async () => {
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return
    }
  }
  if (auth.user?.role !== 'ADMIN') {
    ElMessage.warning('需要管理员权限')
    router.replace('/')
    return
  }
  await loadTracks()
  await Promise.all([load(), loadOptions()])
})

async function loadTracks() {
  try {
    const data = await adminTrackApi.list()
    tracks.value = data.filter((track) => track.status !== 'INACTIVE')
  } catch {
    // 拦截器已提示
  }
}

async function loadOptions() {
  try {
    Object.assign(questionOptions, await adminQuestionApi.options())
  } catch {
    // 下拉选项加载失败时仍可手动输入
  }
}

async function load() {
  loading.value = true
  try {
    const data = await adminQuestionApi.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      trackId: activeTrackId.value ?? undefined
    })
    records.value = data.records
    total.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function switchTrack(trackId: number | null) {
  activeTrackId.value = trackId
  page.value = 1
  load()
}

function openCreate() {
  formTags.value = []
  Object.assign(form, {
    category: defaultCategory(),
    tags: '',
    difficulty: 1,
    question: '',
    referenceAnswer: '',
    source: '个人整理',
    status: 'ACTIVE',
    trackIds: activeTrackId.value ? [activeTrackId.value] : []
  })
  dialogVisible.value = true
}

function defaultCategory(): string {
  const track = tracks.value.find((item) => item.id === activeTrackId.value)
  if (track?.primaryLanguage === 'JAVA') return 'Java 基础'
  if (track?.primaryLanguage === 'JS_TS') return 'JavaScript'
  return 'Java 基础'
}

async function submit() {
  if (!form.category.trim()) {
    ElMessage.warning('请选择或新建分类')
    return
  }
  const tags = formTags.value.map((tag) => tag.trim()).filter(Boolean)
  if (!form.question.trim() || !form.referenceAnswer.trim() || tags.length === 0) {
    ElMessage.warning('题目、参考答案、标签不能为空')
    return
  }
  try {
    await adminQuestionApi.create({
      ...form,
      category: form.category.trim(),
      source: form.source.trim() || '个人整理',
      tags: tags.join(',')
    })
    ElMessage.success('题目已创建')
    dialogVisible.value = false
    await Promise.all([load(), loadTracks(), loadOptions()])
  } catch {
    // 拦截器已提示
  }
}

function openImport() {
  extractResult.value = null
  importRows.value = []
  importTrackIds.value = activeTrackId.value ? [activeTrackId.value] : []
  importDefaults.category = defaultCategory()
  importDefaults.difficulty = 1
  importDefaults.source = '文档导入'
  importVisible.value = true
}

async function handleDocumentUpload(options: UploadRequestOptions) {
  extracting.value = true
  extractResult.value = null
  importRows.value = []
  try {
    const data = await adminQuestionApi.extractDocument(options.file as File, {
      ...importDefaults
    })
    extractResult.value = data
    importRows.value = data.items.map((item, index) => ({
      ...item,
      tags: [...item.tags],
      id: index + 1,
      selected: true
    }))
    options.onSuccess(data)
    if (data.truncated) {
      ElMessage.warning('文档较长，仅解析了前 60000 字，建议拆分上传')
    } else {
      ElMessage.success(`已识别 ${data.total} 道题，请勾选并检查`)
    }
  } catch (error) {
    options.onError(error as never)
  } finally {
    extracting.value = false
  }
}

function toggleAllRows(value: boolean | string | number) {
  const checked = Boolean(value)
  importRows.value.forEach((row) => {
    row.selected = checked
  })
}

function openRowEditor(row: ImportRow) {
  editingRow.value = row
  Object.assign(rowForm, {
    question: row.question,
    referenceAnswer: row.referenceAnswer,
    category: row.category,
    difficulty: row.difficulty,
    tags: [...row.tags],
    source: row.source
  })
  rowDialogVisible.value = true
}

function saveRowEdit() {
  if (!editingRow.value) {
    return
  }
  const tags = rowForm.tags.map((tag) => tag.trim()).filter(Boolean)
  if (!rowForm.question.trim() || !rowForm.category.trim()) {
    ElMessage.warning('题目和分类不能为空')
    return
  }
  if (tags.length === 0) {
    ElMessage.warning('请至少保留一个标签')
    return
  }
  Object.assign(editingRow.value, {
    question: rowForm.question.trim(),
    referenceAnswer: rowForm.referenceAnswer.trim(),
    category: rowForm.category.trim(),
    difficulty: rowForm.difficulty,
    tags,
    source: rowForm.source.trim() || '文档导入'
  })
  rowDialogVisible.value = false
  if (!rowForm.referenceAnswer.trim()) {
    ElMessage.warning('已保存，但参考答案为空，导入前需要补充')
  } else {
    ElMessage.success('题目已修改')
  }
}

function removeImportRow(row: ImportRow) {
  importRows.value = importRows.value.filter((item) => item.id !== row.id)
}

async function submitImport() {
  const selected = selectedImportRows.value
  if (selected.length === 0) {
    ElMessage.warning('请至少勾选一道题')
    return
  }
  const invalid = selected.findIndex(
    (row) =>
      !row.question.trim() ||
      !row.referenceAnswer.trim() ||
      !row.category.trim() ||
      !row.tags.some((tag) => tag.trim())
  )
  if (invalid >= 0) {
    ElMessage.warning(`第 ${selected[invalid].id} 道题缺少参考答案或必填项，请点击「修改」补充`)
    return
  }
  if (importTrackIds.value.length === 0) {
    try {
      await ElMessageBox.confirm(
        '未选择岗位题库，导入的题目不会参与面试检索。仍要导入吗？',
        '提示',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }

  importing.value = true
  try {
    const questions = selected.map((row) => ({
      question: row.question.trim(),
      referenceAnswer: row.referenceAnswer.trim(),
      category: row.category.trim(),
      difficulty: row.difficulty,
      tags: row.tags.map((tag) => tag.trim()).filter(Boolean),
      source: row.source.trim() || '文档导入'
    }))
    const result = await adminQuestionApi.importQuestions({
      questions,
      trackIds: importTrackIds.value
    })
    ElMessage.success(`已导入 ${result.imported} 道题`)
    importVisible.value = false
    await Promise.all([load(), loadTracks(), loadOptions()])
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

async function toggleStatus(row: AdminQuestion) {
  const nextStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const action = nextStatus === 'ACTIVE' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${action}「${row.question}」吗？${nextStatus === 'INACTIVE' ? '禁用后该题不会再被面试检索。' : ''}`,
      `${action}题目`,
      { type: 'warning' }
    )
    await adminQuestionApi.setStatus(row.id, nextStatus)
    ElMessage.success(`题目已${action}`)
    await load()
    await loadTracks()
  } catch {
    // 用户取消或接口失败
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next: number) {
  page.value = next
  load()
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="admin-page">
    <header class="topbar">
      <button class="back-link" type="button" @click="goHome">← 返回岗位</button>
      <BrandBlock suffix="题库管理" />
      <span class="status">ADMIN</span>
    </header>

    <main class="content">
      <section class="hero">
        <div>
          <p class="eyebrow">QUESTION BANK</p>
          <h1>题库管理</h1>
          <p class="subtitle">维护面试题目、参考答案和标签，供面试蓝图检索使用。</p>
        </div>
        <div class="hero-actions">
          <el-button size="large" @click="openImport">从文档导入</el-button>
          <el-button type="primary" size="large" @click="openCreate">新增题目</el-button>
        </div>
      </section>

      <section class="track-tabs">
        <button
          type="button"
          class="track-tab"
          :class="{ active: activeTrackId === null }"
          @click="switchTrack(null)"
        >
          全部题目
        </button>
        <button
          v-for="track in tracks"
          :key="track.id"
          type="button"
          class="track-tab"
          :class="{ active: activeTrackId === track.id }"
          @click="switchTrack(track.id)"
        >
          {{ track.name }}
          <small>{{ track.questionCount }}</small>
        </button>
      </section>

      <section class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索题目 / 分类 / 标签"
          clearable
          style="max-width: 360px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">搜索</el-button>
      </section>

      <el-table v-loading="loading" :data="records" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="question" label="题目" min-width="260" show-overflow-tooltip />
        <el-table-column label="所属题库" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="name in row.trackNames"
              :key="name"
              size="small"
              effect="plain"
              style="margin-right: 4px"
            >
              {{ name }}
            </el-tag>
            <span v-if="!row.trackNames?.length" class="muted">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="80">
          <template #default="{ row }">{{ difficultyLabels[row.difficulty] || row.difficulty }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              link
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="changePage"
        />
      </div>
    </main>

    <el-dialog v-model="dialogVisible" title="新增题目" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="分类">
            <el-select
              v-model="form.category"
              filterable
              allow-create
              default-first-option
              placeholder="选择或新建分类"
              style="width: 100%"
            >
              <el-option v-for="category in categoryChoices" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button :value="1">简单</el-radio-button>
              <el-radio-button :value="2">中等</el-radio-button>
              <el-radio-button :value="3">困难</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio-button value="ACTIVE">启用</el-radio-button>
              <el-radio-button value="INACTIVE">禁用</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="所属题库">
          <el-select
            v-model="form.trackIds"
            multiple
            placeholder="选择该题目所属的岗位题库"
            style="width: 100%"
          >
            <el-option
              v-for="track in tracks"
              :key="track.id"
              :label="`${track.name} · ${track.level}`"
              :value="track.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="formTags"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择或输入标签"
            style="width: 100%"
          >
            <el-option v-for="tag in formTagChoices" :key="tag" :label="tag" :value="tag" />
          </el-select>
          <p class="field-hint">可多选，也可直接输入新标签，保存后会出现在下拉选项中。</p>
        </el-form-item>
        <el-form-item label="题目">
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="form.referenceAnswer" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select
            v-model="form.source"
            filterable
            allow-create
            default-first-option
            placeholder="选择或新建来源"
            style="width: 100%"
          >
            <el-option v-for="source in sourceChoices" :key="source" :label="source" :value="source" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importVisible"
      title="从文档导入题目"
      width="min(1180px, 94vw)"
      :close-on-click-modal="false"
      class="import-dialog"
    >
      <div class="import-body">
        <section class="import-defaults">
          <div class="import-section-head">
            <strong>识别设置</strong>
            <span class="muted">上传时作为缺失字段的默认值；识别后仍可逐题修改</span>
          </div>
          <el-form label-position="top">
            <div class="import-grid">
              <el-form-item label="默认分类">
              <el-select
                v-model="importDefaults.category"
                filterable
                allow-create
                default-first-option
                placeholder="选择或新建分类"
                style="width: 100%"
              >
                <el-option
                  v-for="category in importCategoryChoices"
                  :key="category"
                  :label="category"
                  :value="category"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="默认难度">
              <el-radio-group v-model="importDefaults.difficulty">
                <el-radio-button
                  v-for="choice in difficultyChoices"
                  :key="choice.value"
                  :value="choice.value"
                >
                  {{ choice.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="来源">
              <el-select
                v-model="importDefaults.source"
                filterable
                allow-create
                default-first-option
                placeholder="选择或新建来源"
                style="width: 100%"
              >
                <el-option
                  v-for="source in importSourceChoices"
                  :key="source"
                  :label="source"
                  :value="source"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="导入到岗位题库">
            <el-select
              v-model="importTrackIds"
              multiple
              placeholder="可多选；不选则只进入公共题库"
              style="width: 100%"
            >
              <el-option
                v-for="track in tracks"
                :key="track.id"
                :label="`${track.name} · ${track.level}`"
                :value="track.id"
              />
            </el-select>
          </el-form-item>
          </el-form>
        </section>

        <el-upload
          class="import-upload"
          drag
          accept=".pdf,.docx,.txt,.md,.markdown"
          :show-file-list="false"
          :http-request="handleDocumentUpload"
          :disabled="extracting"
        >
          <div class="upload-inner">
            <strong>{{ extracting ? '正在读取并识别题目…' : '拖拽文档到此处，或点击选择文件' }}</strong>
            <small>支持 PDF / DOCX / TXT / Markdown，最大 10MB；扫描版 PDF 需要先 OCR。</small>
            <small>文档内容会发送给当前激活的 AI 配置用于识别，识别失败时改用本地规则。</small>
          </div>
        </el-upload>

        <el-alert
          v-if="extracting"
          class="import-alert"
          type="info"
          :closable="false"
          show-icon
          title="正在解析文档，通常需要几秒到一分钟，请勿关闭弹窗。"
        />

        <template v-if="extractResult">
          <el-alert class="import-alert" type="success" :closable="false" show-icon>
            <template #title>
              已识别 {{ extractResult.total }} 道题（{{
                extractResult.aiUsed ? 'AI 解析' : '规则解析'
              }}）· {{ extractResult.fileName }}
            </template>
          </el-alert>
          <el-alert
            v-if="extractResult.truncated"
            class="import-alert"
            type="warning"
            :closable="false"
            show-icon
            title="文档较长，仅解析了前 60000 字，建议拆分后再上传。"
          />

          <div class="import-toolbar">
            <span>
              已勾选 <strong>{{ selectedCount }}</strong> / {{ importRows.length }} 道
            </span>
            <el-button size="small" @click="toggleAllRows(!allSelected)">
              {{ allSelected ? '取消全选' : '全选' }}
            </el-button>
          </div>

          <el-table :data="importRows" row-key="id" max-height="430" style="width: 100%">
            <el-table-column width="48" align="center">
              <template #header>
                <el-checkbox
                  :model-value="allSelected"
                  :indeterminate="someSelected"
                  @change="toggleAllRows"
                />
              </template>
              <template #default="{ row }">
                <el-checkbox v-model="row.selected" />
              </template>
            </el-table-column>
            <el-table-column type="index" label="#" width="52" />
            <el-table-column label="题目" min-width="300">
              <template #default="{ row }">
                <div class="preview-text" :title="row.question">{{ row.question }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="130" show-overflow-tooltip />
            <el-table-column label="难度" width="78">
              <template #default="{ row }">
                {{ difficultyLabels[row.difficulty] || row.difficulty }}
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="180">
              <template #default="{ row }">
                <el-tag
                  v-for="tag in row.tags"
                  :key="tag"
                  size="small"
                  effect="plain"
                  class="preview-tag"
                >
                  {{ tag }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="参考答案" min-width="260">
              <template #default="{ row }">
                <span v-if="row.referenceAnswer" class="preview-text" :title="row.referenceAnswer">
                  {{ row.referenceAnswer }}
                </span>
                <span v-else class="missing-answer">待补充</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRowEditor(row)">修改</el-button>
                <el-button link type="danger" @click="removeImportRow(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <el-empty
          v-else-if="!extracting"
          description="上传文档后，识别结果会显示在这里"
          :image-size="72"
        />
      </div>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!extractResult || selectedCount === 0"
          @click="submitImport"
        >
          导入选中题目（{{ selectedCount }}）
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rowDialogVisible"
      title="修改识别结果"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="分类">
            <el-select
              v-model="rowForm.category"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option
                v-for="category in rowCategoryChoices"
                :key="category"
                :label="category"
                :value="category"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-radio-group v-model="rowForm.difficulty">
              <el-radio-button
                v-for="choice in difficultyChoices"
                :key="choice.value"
                :value="choice.value"
              >
                {{ choice.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="来源">
            <el-select
              v-model="rowForm.source"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option
                v-for="source in rowSourceChoices"
                :key="source"
                :label="source"
                :value="source"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-select
            v-model="rowForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="选择或输入标签"
            style="width: 100%"
          >
            <el-option v-for="tag in rowTagChoices" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目">
          <el-input v-model="rowForm.question" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="rowForm.referenceAnswer" type="textarea" :rows="8" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rowDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRowEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
}

.topbar {
  height: 68px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid #e4ecf5;
  backdrop-filter: blur(12px);
}

.brand {
  color: #17375e;
  font-size: 20px;
  font-weight: 800;
}

.back-link {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}

.status {
  color: #7f8c9d;
  font-size: 13px;
}

.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 36px 28px 72px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.field-hint {
  margin: 6px 0 0;
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.5;
}

.eyebrow {
  margin: 0 0 10px;
  color: #2e75b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.hero h1 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: clamp(28px, 3vw, 38px);
}

.subtitle {
  margin: 0;
  color: #7f8c9d;
  font-size: 14px;
}

.track-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.track-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid #dbe6f2;
  background: #fff;
  color: #5b6b7f;
  font-size: 13px;
  cursor: pointer;
}

.track-tab small {
  color: #a0aab6;
  font-size: 11px;
}

.track-tab.active {
  border-color: #1f4e79;
  background: #1f4e79;
  color: #fff;
}

.track-tab.active small {
  color: rgba(255, 255, 255, 0.75);
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.muted {
  color: #a0aab6;
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.import-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.import-defaults {
  padding: 16px 18px 4px;
  border: 1px solid #e4ecf5;
  border-radius: 14px;
  background: #f8fbff;
}

.import-section-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  color: #17375e;
}

.import-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 14px;
}

.import-upload {
  display: block;
  width: 100%;
}

.import-upload :deep(.el-upload) {
  width: 100%;
}

.import-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px 18px;
  border-radius: 14px;
  background: #fff;
}

.upload-inner {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #17375e;
}

.upload-inner small {
  color: #8a97a8;
  font-size: 12px;
  line-height: 1.6;
}

.import-alert {
  margin: 0;
}

.import-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #6b7a8c;
  font-size: 13px;
}

.preview-text {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  white-space: pre-wrap;
  color: #17375e;
  line-height: 1.55;
}

.preview-tag {
  margin: 0 4px 4px 0;
}

.missing-answer {
  color: #d97706;
  font-size: 12px;
}

@media (max-width: 960px) {
  .import-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .topbar {
    padding: 0 18px;
  }

  .content {
    padding: 24px 16px 56px;
  }

  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .form-grid,
  .import-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.import-dialog .el-dialog__body {
  max-height: 68vh;
  overflow-y: auto;
}
</style>
