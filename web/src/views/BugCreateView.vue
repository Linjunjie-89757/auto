<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Close, Delete, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Editor, EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Underline from '@tiptap/extension-underline'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { Extension } from '@tiptap/core'
import { TextStyle } from '@tiptap/extension-text-style'
import { platformApi } from '../api/platform'
import { useWorkspace } from '../composables/useWorkspace'
import type { CreateBugPayload, UserItem, WorkspaceItem } from '../types/api'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    fontSize: {
      setFontSize: (size: string) => ReturnType
      unsetFontSize: () => ReturnType
    }
  }
}

type PendingBugFile = {
  id: string
  file: File
  previewUrl: string | null
}

const FontSize = Extension.create({
  name: 'fontSize',
  addGlobalAttributes() {
    return [{
      types: ['textStyle'],
      attributes: {
        fontSize: {
          default: null,
          parseHTML: element => element.style.fontSize || null,
          renderHTML: attributes => (attributes.fontSize ? { style: `font-size: ${attributes.fontSize}` } : {}),
        },
      },
    }]
  },
  addCommands() {
    return {
      setFontSize: size => ({ chain }) => chain().setMark('textStyle', { fontSize: size }).run(),
      unsetFontSize: () => ({ chain }) => chain().setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run(),
    }
  },
})

const router = useRouter()
const { workspaceCode, isAllScope } = useWorkspace()

const loading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const uploadInput = ref<HTMLInputElement | null>(null)
const pendingFiles = ref<PendingBugFile[]>([])

const form = reactive<CreateBugPayload & { workspaceCode: string }>({
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const headingOptions = [
  { label: '正文', value: 'paragraph' },
  { label: 'H1', value: 'h1' },
  { label: 'H2', value: 'h2' },
  { label: 'H3', value: 'h3' },
]
const fontSizeOptions = [
  { label: '默认', value: 'default' },
  { label: '12px', value: '12px' },
  { label: '14px', value: '14px' },
  { label: '16px', value: '16px' },
  { label: '18px', value: '18px' },
]
const priorityOptions = ['P0', 'P1', 'P2', 'P3']
const severityOptions = [
  { label: '致命', value: 'CRITICAL' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
]

const editor = useEditor({
  content: '<p></p>',
  extensions: [
    StarterKit.configure({ heading: { levels: [1, 2, 3] } }),
    TextStyle,
    FontSize,
    Underline,
    Highlight,
    TextAlign.configure({ types: ['heading', 'paragraph'], alignments: ['left', 'center', 'right', 'justify'] }),
    TaskList,
    TaskItem.configure({ nested: false }),
    Placeholder.configure({ placeholder: '请输入缺陷内容' }),
  ],
  editorProps: {
    attributes: {
      class: 'bug-create-rich-input',
    },
  },
  onUpdate: ({ editor: currentEditor }) => {
    form.description = currentEditor.getHTML()
  },
})

const bugStatus = computed(() => (form.assigneeId === null ? '待指派' : '已指派'))
const currentHeadingLabel = computed(() => {
  if (!editor.value) {
    return '正文'
  }
  const matched = headingOptions.find((item) => {
    if (item.value === 'paragraph') {
      return editor.value?.isActive('paragraph')
    }
    return editor.value?.isActive('heading', { level: Number(item.value.replace('h', '')) })
  })
  return matched?.label ?? '正文'
})
const currentFontSizeLabel = computed(() => {
  const size = editor.value?.getAttributes('textStyle').fontSize as string | undefined
  return fontSizeOptions.find(item => item.value === size)?.label ?? '默认'
})
const canSubmit = computed(() => {
  if (isAllScope.value && !form.workspaceCode) {
    return false
  }
  return form.title.trim().length > 0 && Boolean(editor.value?.getText().trim())
})

onMounted(loadOptions)
onBeforeUnmount(() => {
  editor.value?.destroy()
  clearPendingFiles()
})

async function loadOptions() {
  loading.value = true
  try {
    const [userList, workspaceList] = await Promise.all([
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    users.value = userList
    workspaces.value = workspaceList.filter(item => !item.allScope)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ path: '/bugs', query: { workspace: workspaceCode.value } })
}

function chain(editorInstance?: Editor | null) {
  return editorInstance?.chain().focus()
}

function applyHeading(value: string | number | object) {
  if (!editor.value) {
    return
  }
  if (value === 'paragraph') {
    chain(editor.value)?.setParagraph().run()
    return
  }
  const level = Number(String(value).replace('h', ''))
  chain(editor.value)?.toggleHeading({ level: level as 1 | 2 | 3 }).run()
}

function applyFontSize(value: string | number | object) {
  if (!editor.value) {
    return
  }
  if (value === 'default') {
    editor.value.commands.unsetFontSize()
    return
  }
  chain(editor.value)?.setFontSize(String(value)).run()
}

function openAttachmentPicker() {
  uploadInput.value?.click()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) {
    return
  }
  const nextItems = files.map((file, index) => ({
    id: `bug-file-${Date.now()}-${index}-${file.name}`,
    file,
    previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : null,
  }))
  pendingFiles.value = [...pendingFiles.value, ...nextItems]
}

function removePendingFile(id: string) {
  const target = pendingFiles.value.find(item => item.id === id)
  if (target?.previewUrl) {
    URL.revokeObjectURL(target.previewUrl)
  }
  pendingFiles.value = pendingFiles.value.filter(item => item.id !== id)
}

function clearPendingFiles() {
  pendingFiles.value.forEach((item) => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  pendingFiles.value = []
}

async function submitBug(keepOpen = false) {
  if (!canSubmit.value) {
    ElMessage.warning('请先补充必填信息')
    return
  }
  saving.value = true
  try {
    const targetWorkspaceCode = isAllScope.value ? form.workspaceCode : workspaceCode.value
    const payload: CreateBugPayload = {
      workspaceCode: isAllScope.value ? form.workspaceCode : undefined,
      title: form.title.trim(),
      description: editor.value?.getHTML() ?? '',
      priority: form.priority,
      severity: form.severity,
      assigneeId: form.assigneeId,
      tags: form.tags,
    }
    const createdBug = await platformApi.createBug(workspaceCode.value, payload)
    if (pendingFiles.value.length) {
      await platformApi.uploadBugAttachment(targetWorkspaceCode, createdBug.id, pendingFiles.value.map(item => item.file))
    }
    ElMessage.success('缺陷创建成功')
    if (keepOpen) {
      form.title = ''
      form.description = ''
      form.assigneeId = null
      form.tags = []
      editor.value?.commands.clearContent()
      clearPendingFiles()
      return
    }
    goBack()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <section v-loading="loading" class="bug-create-page">
    <div class="bug-create-shell">
      <header class="bug-create-header">
        <h1 class="bug-create-title">创建缺陷</h1>
        <button type="button" class="bug-create-close" aria-label="关闭" @click="goBack">
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <el-form label-position="top" class="bug-create-form">
        <main class="bug-create-main">
          <el-form-item label="缺陷名称" required class="bug-create-form-item">
            <el-input v-model="form.title" placeholder="请输入缺陷名称" />
          </el-form-item>

          <el-form-item label="缺陷内容" required class="bug-create-form-item">
            <div class="bug-create-content-panel">
              <div class="bug-create-toolbar">
                <el-tooltip content="撤销" placement="top">
                  <el-button text class="bug-create-toolbar-button" :disabled="!editor?.can().undo()" @click="chain(editor)?.undo().run()">
                    <el-icon><RefreshLeft /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="恢复" placement="top">
                  <el-button text class="bug-create-toolbar-button" :disabled="!editor?.can().redo()" @click="chain(editor)?.redo().run()">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                </el-tooltip>
                <span class="bug-create-toolbar-divider"></span>
                <el-dropdown trigger="click" @command="applyHeading">
                  <el-button text class="bug-create-toolbar-select">{{ currentHeadingLabel }}</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="item in headingOptions" :key="item.value" :command="item.value">
                        {{ item.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-dropdown trigger="click" @command="applyFontSize">
                  <el-button text class="bug-create-toolbar-select">{{ currentFontSizeLabel }}</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="item in fontSizeOptions" :key="item.value" :command="item.value">
                        {{ item.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <span class="bug-create-toolbar-divider"></span>
                <el-tooltip content="加粗" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('bold') }" @click="chain(editor)?.toggleBold().run()">B</el-button>
                </el-tooltip>
                <el-tooltip content="斜体" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('italic') }" @click="chain(editor)?.toggleItalic().run()">I</el-button>
                </el-tooltip>
                <el-tooltip content="下划线" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('underline') }" @click="chain(editor)?.toggleUnderline().run()">U</el-button>
                </el-tooltip>
                <el-tooltip content="删除线" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('strike') }" @click="chain(editor)?.toggleStrike().run()">S</el-button>
                </el-tooltip>
                <span class="bug-create-toolbar-divider"></span>
                <el-tooltip content="无序列表" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('bulletList') }" @click="chain(editor)?.toggleBulletList().run()">•</el-button>
                </el-tooltip>
                <el-tooltip content="有序列表" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('orderedList') }" @click="chain(editor)?.toggleOrderedList().run()">1.</el-button>
                </el-tooltip>
                <el-tooltip content="任务列表" placement="top">
                  <el-button text class="bug-create-toolbar-button" :class="{ 'is-active': editor?.isActive('taskList') }" @click="chain(editor)?.toggleTaskList().run()">☑</el-button>
                </el-tooltip>
                <span class="bug-create-toolbar-divider"></span>
                <el-tooltip content="左对齐" placement="top">
                  <el-button text class="bug-create-toolbar-button" @click="chain(editor)?.setTextAlign('left').run()">≡</el-button>
                </el-tooltip>
                <el-tooltip content="居中" placement="top">
                  <el-button text class="bug-create-toolbar-button" @click="chain(editor)?.setTextAlign('center').run()">≣</el-button>
                </el-tooltip>
                <el-tooltip content="右对齐" placement="top">
                  <el-button text class="bug-create-toolbar-button" @click="chain(editor)?.setTextAlign('right').run()">≡</el-button>
                </el-tooltip>
                <span class="bug-create-toolbar-divider"></span>
                <el-tooltip content="清除格式" placement="top">
                  <el-button text class="bug-create-toolbar-button" @click="editor?.chain().focus().unsetAllMarks().clearNodes().run()">Tx</el-button>
                </el-tooltip>
                <el-tooltip content="清空内容" placement="top">
                  <el-button text class="bug-create-toolbar-button" @click="editor?.commands.clearContent()">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
              <div class="bug-create-editor">
                <EditorContent v-if="editor" :editor="editor" />
              </div>
            </div>
          </el-form-item>

          <section class="bug-create-evidence">
            <div class="bug-create-evidence-title">执行截图（{{ pendingFiles.length }}）</div>
            <div class="bug-create-evidence-meta">点击加号上传，支持图片和任意附件</div>
            <input ref="uploadInput" type="file" multiple style="display: none" @change="handleFileChange">
            <div class="bug-create-evidence-files">
              <div v-for="item in pendingFiles" :key="item.id" class="bug-create-file">
                <button type="button" class="bug-create-file-remove" @click="removePendingFile(item.id)">×</button>
                <div class="bug-create-file-preview">
                  <img v-if="item.previewUrl" :src="item.previewUrl" :alt="item.file.name">
                  <span v-else>{{ (item.file.name.split('.').pop() || 'FILE').slice(0, 6).toUpperCase() }}</span>
                </div>
                <div class="bug-create-file-name" :title="item.file.name">{{ item.file.name }}</div>
              </div>
              <button type="button" class="bug-create-evidence-add" :disabled="saving" @click="openAttachmentPicker">
                <span class="bug-create-evidence-plus">+</span>
                <span>上传截图</span>
              </button>
            </div>
          </section>
        </main>

        <aside class="bug-create-side">
          <el-form-item v-if="isAllScope" label="所属空间" required class="bug-create-form-item">
            <el-select v-model="form.workspaceCode" placeholder="请选择所属空间">
              <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理人" class="bug-create-form-item">
            <el-select v-model="form.assigneeId" clearable placeholder="请选择">
              <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" required class="bug-create-form-item">
            <el-input :model-value="bugStatus" disabled />
          </el-form-item>
          <el-form-item label="优先级" required class="bug-create-form-item">
            <el-select v-model="form.priority" placeholder="请选择">
              <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="严重程度" required class="bug-create-form-item">
            <el-select v-model="form.severity" placeholder="请选择">
              <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签" class="bug-create-form-item">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="输入内容后回车可直接添加标签"
            />
          </el-form-item>
        </aside>
      </el-form>

      <footer class="bug-create-footer">
        <el-button @click="goBack">取消</el-button>
        <el-button :loading="saving" :disabled="!canSubmit" @click="submitBug(true)">保存并继续创建</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submitBug(false)">创建</el-button>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.bug-create-page {
  min-height: calc(100vh - 96px);
  padding: 0;
}

.bug-create-shell {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: calc(100vh - 96px);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.bug-create-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
}

.bug-create-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
  color: #323233;
}

.bug-create-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #323233;
  cursor: pointer;
}

.bug-create-close:hover {
  background: #f5f7fa;
}

.bug-create-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 322px;
  gap: 28px;
  min-height: 0;
  padding: 34px 48px 32px 34px;
}

.bug-create-main,
.bug-create-side {
  min-width: 0;
}

.bug-create-main {
  display: grid;
  align-content: start;
  gap: 18px;
}

.bug-create-side {
  display: grid;
  align-content: start;
  gap: 20px;
  padding-left: 16px;
  border-left: 1px solid #ebeef5;
}

.bug-create-form-item {
  margin-bottom: 0;
}

.bug-create-page :deep(.el-form-item__label) {
  margin-bottom: 8px;
  padding: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  color: #323233;
}

.bug-create-page :deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #f56c6c;
}

.bug-create-page :deep(.el-input__wrapper),
.bug-create-page :deep(.el-select__wrapper) {
  border-radius: 3px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.bug-create-content-panel,
.bug-create-evidence {
  border: 1px solid #dcdfe6;
  border-radius: 2px;
  background: #fff;
}

.bug-create-toolbar {
  display: flex;
  align-items: center;
  gap: 1px;
  min-height: 40px;
  padding: 5px 10px;
  border-bottom: 1px solid #ebeef5;
  background: #fafbfc;
  overflow-x: auto;
  scrollbar-width: thin;
}

.bug-create-toolbar-button {
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 4px;
  color: #475467;
  font-size: 13px;
  font-weight: 600;
}

.bug-create-toolbar-button.is-active {
  background: #eaf2ff;
  color: #2563eb;
}

.bug-create-toolbar-select {
  height: 30px;
  padding: 0 8px;
  border-radius: 4px;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.bug-create-toolbar-divider {
  flex: 0 0 auto;
  width: 1px;
  height: 15px;
  margin: 0 3px;
  background: #dcdfe6;
}

.bug-create-editor {
  padding: 16px;
}

.bug-create-editor :deep(.bug-create-rich-input) {
  min-height: 320px;
  outline: none;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  word-break: break-word;
}

.bug-create-editor :deep(.bug-create-rich-input p) {
  margin: 0 0 12px;
}

.bug-create-editor :deep(.bug-create-rich-input p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: #a8abb2;
  pointer-events: none;
}

.bug-create-editor :deep(.bug-create-rich-input ul),
.bug-create-editor :deep(.bug-create-rich-input ol) {
  padding-left: 22px;
}

.bug-create-editor :deep(.bug-create-rich-input ul[data-type="taskList"]) {
  list-style: none;
  padding-left: 0;
}

.bug-create-editor :deep(.bug-create-rich-input ul[data-type="taskList"] li) {
  display: flex;
  gap: 8px;
}

.bug-create-evidence {
  padding: 18px 16px 16px;
}

.bug-create-evidence-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  color: #323233;
}

.bug-create-evidence-meta {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: #667085;
}

.bug-create-evidence-files {
  display: grid;
  grid-template-columns: repeat(auto-fill, 104px);
  gap: 12px;
  margin-top: 16px;
}

.bug-create-file {
  position: relative;
  display: grid;
  gap: 6px;
  width: 104px;
}

.bug-create-file-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  cursor: pointer;
}

.bug-create-file-preview,
.bug-create-evidence-add {
  display: grid;
  place-items: center;
  width: 104px;
  height: 104px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  background: #fcfcfd;
  color: #475467;
  overflow: hidden;
}

.bug-create-file-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bug-create-file-name {
  overflow: hidden;
  color: #98a2b3;
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-create-evidence-add {
  gap: 6px;
  padding: 10px;
  cursor: pointer;
}

.bug-create-evidence-add:disabled {
  cursor: wait;
  opacity: 0.7;
}

.bug-create-evidence-plus {
  font-size: 30px;
  line-height: 1;
}

.bug-create-footer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
  padding: 14px 20px 16px;
  border-top: 1px solid #ebeef5;
  border-radius: 0 0 8px 8px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

@media (max-width: 1200px) {
  .bug-create-form {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .bug-create-side {
    padding-left: 0;
    border-left: 0;
  }
}
</style>
