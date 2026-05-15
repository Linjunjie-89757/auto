<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import {
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
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
import type { UserItem } from '../types/api'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    fontSize: {
      setFontSize: (size: string) => ReturnType
      unsetFontSize: () => ReturnType
    }
  }
}

type BugEditorForm = {
  workspaceCode: string
  title: string
  priority: string
  severity: string
  assigneeId: number | null
  tags: string[]
  description: string
}

type BugSourceContext = {
  caseNo: string
  caseTitle: string
  modulePath: string
  executionStatus: string
  actualResult: string
  precondition?: string
  steps?: string
  expectedResult?: string
}

type PendingBugFile = {
  id: string
  name: string
  size: number
  kind: 'attachment' | 'screenshot'
  previewUrl?: string | null
}

const FontSize = Extension.create({
  name: 'fontSize',
  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontSize: {
            default: null,
            parseHTML: element => element.style.fontSize || null,
            renderHTML: attributes => {
              if (!attributes.fontSize) {
                return {}
              }
              return {
                style: `font-size: ${attributes.fontSize}`,
              }
            },
          },
        },
      },
    ]
  },
  addCommands() {
    return {
      setFontSize: size => ({ chain }) => chain().setMark('textStyle', { fontSize: size }).run(),
      unsetFontSize: () => ({ chain }) => chain().setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run(),
    }
  },
})

const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  form: BugEditorForm
  saving: boolean
  canSubmit: boolean
  users: UserItem[]
  sourceContext?: BugSourceContext | null
  pendingFiles?: PendingBugFile[]
}>(), {
  title: '创建缺陷',
  sourceContext: null,
  pendingFiles: () => [],
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit'): void
  (event: 'submit-and-continue'): void
  (event: 'add-files', files: File[]): void
  (event: 'remove-file', id: string): void
}>()

const severityOptions = [
  { label: '致命', value: 'CRITICAL' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
]

const priorityOptions = [
  { label: 'P0', value: 'P0' },
  { label: 'P1', value: 'P1' },
  { label: 'P2', value: 'P2' },
  { label: 'P3', value: 'P3' },
]

const headingOptions = [
  { label: '正文', value: 'paragraph' },
  { label: 'H1', value: 'h1' },
  { label: 'H2', value: 'h2' },
  { label: 'H3', value: 'h3' },
  { label: 'H4', value: 'h4' },
  { label: 'H5', value: 'h5' },
  { label: 'H6', value: 'h6' },
]

const fontSizeOptions = [
  { label: '默认', value: 'default' },
  { label: '12px', value: '12px' },
  { label: '14px', value: '14px' },
  { label: '16px', value: '16px' },
  { label: '18px', value: '18px' },
]

const bugStatus = computed({
  get: () => (props.form.assigneeId === null ? 'TODO' : 'ASSIGNED'),
  set: () => {},
})

const statusOptions = computed(() => (
  bugStatus.value === 'ASSIGNED'
    ? [{ label: '已指派', value: 'ASSIGNED' }]
    : [{ label: '待处理', value: 'TODO' }]
))

const isEditMode = computed(() => props.title?.includes('编辑'))
const primarySubmitText = computed(() => (isEditMode.value ? '保存' : '创建'))
const currentHeadingLabel = computed(() => {
  if (!editor.value) {
    return '正文'
  }
  const matched = headingOptions.find((item) => {
    if (item.value === 'paragraph') {
      return editor.value?.isActive('paragraph')
    }
    const level = Number(item.value.replace('h', ''))
    return editor.value?.isActive('heading', { level })
  })
  return matched?.label ?? '正文'
})
const currentFontSizeLabel = computed(() => {
  const size = editor.value?.getAttributes('textStyle').fontSize as string | undefined
  return fontSizeOptions.find(item => item.value === size)?.label ?? '默认'
})

let syncingFromEditor = false
let syncingFromForm = false

const editor = useEditor({
  content: normalizeEditorContent(props.form.description),
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [1, 2, 3, 4, 5, 6],
      },
    }),
    TextStyle,
    FontSize,
    Underline,
    Highlight,
    TextAlign.configure({
      types: ['heading', 'paragraph'],
      alignments: ['left', 'center', 'right', 'justify'],
    }),
    TaskList,
    TaskItem.configure({
      nested: false,
    }),
    Placeholder.configure({
      placeholder: '请输入缺陷描述',
    }),
  ],
  editorProps: {
    attributes: {
      class: 'bug-editor-rich-input',
    },
    handlePaste(view, event) {
      const text = event.clipboardData?.getData('text/plain')
      if (!text) {
        return false
      }
      event.preventDefault()
      view.dispatch(view.state.tr.insertText(text))
      return true
    },
  },
  onUpdate: ({ editor: currentEditor }) => {
    syncingFromEditor = true
    props.form.description = currentEditor.getHTML()
    queueMicrotask(() => {
      syncingFromEditor = false
    })
  },
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      syncEditorFromForm()
    }
  },
)

watch(
  () => props.form.description,
  () => {
    if (!syncingFromEditor) {
      syncEditorFromForm()
    }
  },
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

function normalizeEditorContent(content: string) {
  const normalizedText = content.replace(/\r\n/g, '\n')
  const trimmed = normalizedText.trim()
  if (!trimmed) {
    return '<p></p>'
  }
  if (/<[a-z][\s\S]*>/i.test(trimmed)) {
    return trimmed
  }
  return normalizedText
    .split('\n')
    .map(line => `<p>${line ? escapeHtml(line) : '<br>'}</p>`)
    .join('')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function syncEditorFromForm() {
  if (!editor.value || syncingFromForm) {
    return
  }
  const normalized = normalizeEditorContent(props.form.description)
  if (editor.value.getHTML() === normalized) {
    return
  }
  syncingFromForm = true
  editor.value.commands.setContent(normalized, { emitUpdate: false })
  queueMicrotask(() => {
    syncingFromForm = false
  })
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
  chain(editor.value)?.toggleHeading({ level: level as 1 | 2 | 3 | 4 | 5 | 6 }).run()
}

function applyFontSize(value: string | number | object) {
  if (!editor.value) {
    return
  }
  if (value === 'default') {
    editor.value.commands.unsetFontSize()
    return
  }
  editor.value.chain().focus().setFontSize(String(value)).run()
}

function clearFormatting() {
  editor.value?.chain().focus().unsetAllMarks().clearNodes().run()
}

function clearContent() {
  editor.value?.commands.clearContent()
  props.form.description = ''
}

function openAttachmentPicker() {
  const input = document.querySelector<HTMLInputElement>('.bug-editor-drawer input[type="file"]')
  input?.click()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = Array.from(input?.files ?? [])
  if (!files.length) {
    return
  }
  emit('add-files', files)
  if (input) {
    input.value = ''
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="1198px"
    class="bug-editor-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="bug-editor-header">
        <div class="bug-editor-header-title">{{ title }}</div>
      </div>
    </template>

    <el-form label-position="top" class="bug-editor-form">
      <div class="bug-editor-layout">
        <div class="bug-editor-main">
          <el-form-item label="缺陷名称" required class="bug-editor-form-item bug-editor-main-item">
            <el-input v-model="form.title" placeholder="请输入缺陷名称" />
          </el-form-item>

          <el-form-item label="缺陷内容" required class="bug-editor-form-item bug-editor-main-item">
            <div class="bug-editor-content-panel">
              <div class="bug-editor-toolbar">
                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="撤销" placement="top">
                    <el-button text class="bug-editor-toolbar-button" :disabled="!editor?.can().undo()" @click="chain(editor)?.undo().run()">
                      <el-icon><RefreshLeft /></el-icon>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="恢复" placement="top">
                    <el-button text class="bug-editor-toolbar-button" :disabled="!editor?.can().redo()" @click="chain(editor)?.redo().run()">
                      <el-icon><RefreshRight /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>

                <span class="bug-editor-toolbar-divider"></span>

                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="标题/正文" placement="top">
                    <el-dropdown trigger="click" @command="applyHeading">
                      <el-button text class="bug-editor-toolbar-select">
                        <span class="bug-editor-toolbar-select-label">{{ currentHeadingLabel }}</span>
                        <svg viewBox="0 0 20 20" class="bug-editor-toolbar-caret">
                          <path d="m5.5 7.5 4.5 5 4.5-5" />
                        </svg>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            v-for="item in headingOptions"
                            :key="item.value"
                            :command="item.value"
                          >
                            {{ item.label }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </el-tooltip>

                  <el-tooltip content="字号" placement="top">
                    <el-dropdown trigger="click" @command="applyFontSize">
                      <el-button text class="bug-editor-toolbar-select bug-editor-toolbar-size-select">
                        <span class="bug-editor-toolbar-select-label">{{ currentFontSizeLabel }}</span>
                        <svg viewBox="0 0 20 20" class="bug-editor-toolbar-caret">
                          <path d="m5.5 7.5 4.5 5 4.5-5" />
                        </svg>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            v-for="item in fontSizeOptions"
                            :key="item.value"
                            :command="item.value"
                          >
                            {{ item.label }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </el-tooltip>
                </div>

                <span class="bug-editor-toolbar-divider"></span>

                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="加粗" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('bold') }"
                      @click="chain(editor)?.toggleBold().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M6 4.5h5.2a3 3 0 0 1 0 6H6zm0 6h6a3 3 0 1 1 0 6H6z" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="斜体" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('italic') }"
                      @click="chain(editor)?.toggleItalic().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M11.5 4.5h4M4.5 15.5h4M11 4.5l-2 11" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="下划线" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('underline') }"
                      @click="chain(editor)?.toggleUnderline().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M5.5 4.5v4.2a4.5 4.5 0 0 0 9 0V4.5M4.5 15.5h11" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="删除线" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('strike') }"
                      @click="chain(editor)?.toggleStrike().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M6 6.5c.4-1.4 1.8-2.2 4-2.2 2.4 0 4 .9 4 2.6 0 1.7-1.6 2.4-4 2.8-2.5.4-4 1.2-4 3 0 1.8 1.7 2.9 4.2 2.9 2.2 0 3.8-.9 4.4-2.4M4 10h12" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="高亮" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('highlight') }"
                      @click="chain(editor)?.toggleHighlight().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M6 12.5 12.5 6l1.5 1.5L7.5 14H6zM11.7 6.8l1.5-1.5 2 2-1.5 1.5M4 15.5h12" />
                      </svg>
                    </el-button>
                  </el-tooltip>
                </div>

                <span class="bug-editor-toolbar-divider"></span>

                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="无序列表" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('bulletList') }"
                      @click="chain(editor)?.toggleBulletList().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M5 6h.01M5 10h.01M5 14h.01M8 6h7M8 10h7M8 14h7" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="有序列表" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('orderedList') }"
                      @click="chain(editor)?.toggleOrderedList().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4.5 6h1.5v4M4.2 14.5c.5-.8 1.2-1.3 2-1.3.9 0 1.5.5 1.5 1.2 0 .5-.3.9-.8 1.4l-1.6 1.4h2.6M10 6h6M10 10h6M10 14h6" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="任务列表" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive('taskList') }"
                      @click="chain(editor)?.toggleTaskList().run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4.5 5.5h3v3h-3zM5.2 7l.8.8 1.4-1.8M10 7h6M4.5 11.5h3v3h-3zM5.2 13l.8.8 1.4-1.8M10 13h6" />
                      </svg>
                    </el-button>
                  </el-tooltip>
                </div>

                <span class="bug-editor-toolbar-divider"></span>

                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="左对齐" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive({ textAlign: 'left' }) }"
                      @click="chain(editor)?.setTextAlign('left').run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4 5.5h11M4 9h8M4 12.5h11M4 16h7" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="居中" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive({ textAlign: 'center' }) }"
                      @click="chain(editor)?.setTextAlign('center').run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4.5 5.5h11M6 9h8M4.5 12.5h11M6.5 16h7" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="右对齐" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive({ textAlign: 'right' }) }"
                      @click="chain(editor)?.setTextAlign('right').run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M5 5.5h11M8 9h8M5 12.5h11M9 16h7" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="两端对齐" placement="top">
                    <el-button
                      text
                      class="bug-editor-toolbar-button"
                      :class="{ 'is-active': editor?.isActive({ textAlign: 'justify' }) }"
                      @click="chain(editor)?.setTextAlign('justify').run()"
                    >
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4 5.5h12M4 9h12M4 12.5h12M4 16h12" />
                      </svg>
                    </el-button>
                  </el-tooltip>
                </div>

                <span class="bug-editor-toolbar-divider"></span>

                <div class="bug-editor-toolbar-group">
                  <el-tooltip content="清除格式" placement="top">
                    <el-button text class="bug-editor-toolbar-button" @click="clearFormatting">
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M4.5 5.5h8M8.5 5.5v9M6.5 15.5h4M11.5 6.5l4 4M15.5 6.5l-4 4" />
                      </svg>
                    </el-button>
                  </el-tooltip>

                  <el-tooltip content="清空内容" placement="top">
                    <el-button text class="bug-editor-toolbar-button" @click="clearContent">
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                        <path d="M6 6h8M7 6V4.8h6V6M7 8v6.5M10 8v6.5M13 8v6.5M5.5 6l.7 10h7.6l.7-10" />
                      </svg>
                    </el-button>
                  </el-tooltip>
                </div>
              </div>

              <div class="bug-editor-description-input">
                <EditorContent v-if="editor" :editor="editor" />
              </div>
            </div>
          </el-form-item>

          <section class="bug-editor-evidence-card">
            <div class="bug-editor-evidence-header">
              <div>
                <div class="bug-editor-section-title">执行截图（{{ pendingFiles.length }}）</div>
                <div class="bug-editor-evidence-meta">点击加号上传，支持图片和任意附件</div>
              </div>
            </div>
            <input
              type="file"
              multiple
              style="display: none"
              @change="handleFileChange"
            >
            <div class="bug-editor-evidence-files">
              <div
                v-for="item in pendingFiles"
                :key="item.id"
                class="bug-editor-evidence-file"
              >
                <button
                  type="button"
                  class="bug-editor-evidence-file-remove"
                  @click.stop="emit('remove-file', item.id)"
                >
                  ×
                </button>
                <div class="bug-editor-evidence-file-preview">
                  <button
                    v-if="item.previewUrl"
                    type="button"
                    class="bug-editor-evidence-thumb"
                  >
                    <img :src="item.previewUrl" :alt="item.name" class="bug-editor-evidence-thumb-image">
                  </button>
                  <div v-else class="bug-editor-evidence-file-fallback">
                    {{ (item.name.split('.').pop() || 'FILE').slice(0, 6).toUpperCase() }}
                  </div>
                </div>
                <button type="button" class="bug-editor-evidence-file-trigger">
                  <span class="bug-editor-evidence-file-name" :title="item.name">{{ item.name }}</span>
                </button>
              </div>

              <button
                type="button"
                class="bug-editor-evidence-add"
                :disabled="saving"
                @click="openAttachmentPicker"
              >
                <span class="bug-editor-evidence-add-plus">+</span>
                <span class="bug-editor-evidence-add-text">
                  {{ saving ? '上传中...' : '上传截图' }}
                </span>
              </button>
            </div>
          </section>
        </div>

        <aside class="bug-editor-side">
          <el-form-item label="处理人" class="bug-editor-form-item bug-editor-side-item">
            <el-select v-model="form.assigneeId" clearable placeholder="请选择">
              <el-option
                v-for="item in users"
                :key="item.id"
                :label="item.displayName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        
          <el-form-item label="状态" required class="bug-editor-form-item bug-editor-side-item">
            <el-select v-model="bugStatus" placeholder="请选择" disabled>
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        
          <el-form-item label="优先级" required class="bug-editor-form-item bug-editor-side-item">
            <el-select v-model="form.priority" placeholder="请选择">
              <el-option
                v-for="item in priorityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        
          <el-form-item label="严重程度" required class="bug-editor-form-item bug-editor-side-item">
            <el-select v-model="form.severity" placeholder="请选择">
              <el-option
                v-for="item in severityOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        
          <el-form-item label="标签" class="bug-editor-form-item bug-editor-side-item">
            <el-select
              v-model="form.tags"
              class="bug-editor-tag-input"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              popper-class="bug-editor-tag-popper"
              placeholder="输入内容后回车可直接添加标签"
            />
          </el-form-item>
        </aside>
      </div>
    </el-form>

    <template #footer>
      <div class="bug-editor-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button
          v-if="!isEditMode"
          :loading="saving"
          :disabled="!canSubmit"
          @click="emit('submit-and-continue')"
        >
          保存并继续创建
        </el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="emit('submit')">
          {{ primarySubmitText }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.bug-editor-drawer :deep(.el-drawer) {
  min-width: 1198px;
}

.bug-editor-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 13px 14px;
  border-bottom: 1px solid #ebeef5;
}

.bug-editor-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: auto;
}

.bug-editor-drawer :deep(.el-drawer__footer) {
  padding: 14px 20px 16px;
  border-top: 1px solid #ebeef5;
}

.bug-editor-drawer :deep(.el-input__wrapper),
.bug-editor-drawer :deep(.el-select__wrapper) {
  border-radius: 2px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.bug-editor-drawer :deep(.el-select__wrapper) {
  min-height: 34px;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__wrapper) {
  align-items: flex-start;
  min-height: 38px;
  padding: 5px 10px;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__selection) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__selected-item) {
  margin: 0;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-tag) {
  height: 24px;
  margin: 0;
  padding: 0 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f5f7fa;
  color: #344054;
  line-height: 22px;
  box-shadow: none;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-tag .el-tag__content) {
  font-size: 12px;
  line-height: 22px;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-tag .el-tag__close) {
  margin-left: 4px;
  color: #667085;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__input-wrapper) {
  margin: 0;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__input) {
  min-width: 96px;
  margin: 0;
  font-size: 12px;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__placeholder) {
  color: #98a2b3;
}

.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__caret),
.bug-editor-drawer :deep(.bug-editor-tag-input .el-select__suffix) {
  display: none;
}

.bug-editor-drawer :deep(.bug-editor-tag-popper) {
  display: none !important;
}

.bug-editor-drawer :deep(.el-input__inner),
.bug-editor-drawer :deep(.el-select__placeholder) {
  font-size: 12px;
}

.bug-editor-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.bug-editor-header-title {
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.bug-editor-form {
  min-height: 100%;
}

.bug-editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  min-height: calc(100vh - 126px);
}

.bug-editor-main {
  min-width: 0;
  padding: 14px 14px 20px;
}

.bug-editor-main-item,
.bug-editor-content-panel,
.bug-editor-evidence-card {
  width: 100%;
}

.bug-editor-main :deep(.el-form-item__content) {
  display: block;
  width: 100%;
}

.bug-editor-side {
  display: grid;
  align-content: start;
  min-width: 0;
  padding: 14px 14px 20px;
  border-left: 1px solid #ebeef5;
  background: #fff;
}

.bug-editor-form-item {
  margin-bottom: 18px;
}

.bug-editor-side-item {
  margin-bottom: 22px;
}

.bug-editor-form :deep(.el-form-item__label) {
  margin-bottom: 8px;
  padding: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.bug-editor-form :deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #f56c6c;
}

.bug-editor-content-panel,
.bug-editor-evidence-card {
  border: 1px solid #dcdfe6;
  border-radius: 2px;
  background: #fff;
}

.bug-editor-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 1px;
  min-height: 40px;
  padding: 5px 4px;
  border-bottom: 1px solid #ebeef5;
  background: #fafbfc;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.bug-editor-toolbar-group {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 0;
}

.bug-editor-toolbar-divider {
  flex: 0 0 auto;
  width: 1px;
  height: 15px;
  margin: 0;
  background: #dcdfe6;
}

.bug-editor-toolbar-button {
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 6px;
  color: #475467;
}

.bug-editor-toolbar-select {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-width: 50px;
  height: 30px;
  padding: 0 5px;
  border-radius: 6px;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.bug-editor-toolbar-size-select {
  min-width: 56px;
}

.bug-editor-toolbar-select-label {
  line-height: 1;
}

.bug-editor-toolbar-button.is-active {
  background: #eaf2ff;
  color: #2563eb;
}

.bug-editor-toolbar-select:hover,
.bug-editor-toolbar-button:hover {
  background: #f2f4f7;
  color: #344054;
}

.bug-editor-toolbar-button:disabled {
  color: #c0c4cc;
}

.bug-editor-toolbar-caret {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.bug-editor-toolbar-svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.bug-editor-toolbar-button :deep(.el-icon) {
  font-size: 15px;
}

.bug-editor-description-input {
  width: 100%;
  padding: 12px 14px 14px;
}

.bug-editor-description-input :deep(.tiptap) {
  min-height: 280px;
  outline: none;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  word-break: break-word;
}

.bug-editor-description-input :deep(.tiptap p) {
  margin: 0 0 14px;
}

.bug-editor-description-input :deep(.tiptap p:last-child) {
  margin-bottom: 0;
}

.bug-editor-description-input :deep(.tiptap h1),
.bug-editor-description-input :deep(.tiptap h2),
.bug-editor-description-input :deep(.tiptap h3),
.bug-editor-description-input :deep(.tiptap h4),
.bug-editor-description-input :deep(.tiptap h5),
.bug-editor-description-input :deep(.tiptap h6) {
  margin: 0 0 8px;
  line-height: 1.45;
}

.bug-editor-description-input :deep(.tiptap ul),
.bug-editor-description-input :deep(.tiptap ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}

.bug-editor-description-input :deep(.tiptap mark) {
  padding: 0 2px;
  background: #fff3bf;
}

.bug-editor-description-input :deep(.tiptap ul[data-type="taskList"]) {
  list-style: none;
  padding-left: 0;
}

.bug-editor-description-input :deep(.tiptap ul[data-type="taskList"] li) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.bug-editor-description-input :deep(.tiptap ul[data-type="taskList"] li > label) {
  margin-top: 3px;
}

.bug-editor-description-input :deep(.tiptap ul[data-type="taskList"] li > div) {
  flex: 1;
}

.bug-editor-description-input :deep(.tiptap p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
  color: #a8abb2;
}

.bug-editor-section-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.bug-editor-evidence-card {
  margin-top: 4px;
  padding: 16px;
  outline: none;
}

.bug-editor-evidence-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.bug-editor-evidence-meta {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.bug-editor-evidence-files {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 104px));
  gap: 10px;
  margin-top: 14px;
  padding-top: 3px;
  align-items: start;
}

.bug-editor-evidence-file {
  position: relative;
  display: grid;
  grid-template-rows: 104px auto;
  gap: 6px;
  width: 104px;
  background: transparent;
  transition: transform 0.18s ease;
}

.bug-editor-evidence-file:hover {
  transform: translateY(-1px);
}

.bug-editor-evidence-file-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease;
}

.bug-editor-evidence-file:hover .bug-editor-evidence-file-remove,
.bug-editor-evidence-file:focus-within .bug-editor-evidence-file-remove {
  opacity: 1;
  pointer-events: auto;
}

.bug-editor-evidence-file-preview {
  display: flex;
  width: 104px;
  height: 104px;
}

.bug-editor-evidence-file-fallback,
.bug-editor-evidence-thumb {
  width: 100%;
  height: 100%;
}

.bug-editor-evidence-thumb {
  padding: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  background: #f8fafc;
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4);
}

.bug-editor-evidence-thumb-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bug-editor-evidence-file-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fafc 0%, #f2f6fb 100%);
  font-size: 13px;
  font-weight: 700;
  color: #667085;
}

.bug-editor-evidence-file-trigger {
  display: grid;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.bug-editor-evidence-file-name {
  font-size: 11px;
  line-height: 1.45;
  color: #98a2b3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.18s ease;
}

.bug-editor-evidence-file:hover .bug-editor-evidence-file-name,
.bug-editor-evidence-file:focus-within .bug-editor-evidence-file-name {
  color: #667085;
}

.bug-editor-evidence-add {
  display: grid;
  place-items: center;
  gap: 6px;
  width: 104px;
  height: 104px;
  padding: 10px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  background: #fcfcfd;
  color: #667085;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.bug-editor-evidence-add:hover {
  border-color: #98a2b3;
  background: #f8fafc;
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px rgba(208, 213, 221, 0.45);
}

.bug-editor-evidence-add:disabled {
  cursor: wait;
  opacity: 0.7;
}

.bug-editor-evidence-add-plus {
  font-size: 28px;
  line-height: 1;
  font-weight: 500;
  color: #475467;
}

.bug-editor-evidence-add-text {
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
  color: #667085;
}

.bug-editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1280px) {
  .bug-editor-drawer :deep(.el-drawer) {
    min-width: auto;
  }

  .bug-editor-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .bug-editor-side {
    border-left: 0;
    border-top: 1px solid #ebeef5;
  }
}
</style>
