<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import type { Editor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Underline from '@tiptap/extension-underline'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { Extension, Node, mergeAttributes } from '@tiptap/core'
import { TextStyle } from '@tiptap/extension-text-style'
import { ElTooltip } from 'element-plus'
import { RefreshLeft, RefreshRight } from '@element-plus/icons-vue'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    fontSize: {
      setFontSize: (fontSize: string) => ReturnType
      unsetFontSize: () => ReturnType
    }
    bugImage: {
      insertBugImage: (options: { src: string; alt?: string }) => ReturnType
    }
  }
}

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  readonly?: boolean
  minHeight?: number
  allowInlineImage?: boolean
}>(), {
  placeholder: '请输入内容',
  readonly: false,
  minHeight: 280,
  allowInlineImage: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'add-inline-image', payload: { file: File; src: string }): void
}>()

const toolbarTooltipProps = {
  showAfter: 200,
  hideAfter: 0,
  enterable: false,
}

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

const FontSize = Extension.create({
  name: 'fontSize',
  addOptions() {
    return {
      types: ['textStyle'],
    }
  },
  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          fontSize: {
            default: null,
            parseHTML: element => element.style.fontSize || null,
            renderHTML: attributes => {
              if (!attributes.fontSize) {
                return {}
              }
              return { style: `font-size: ${attributes.fontSize}` }
            },
          },
        },
      },
    ]
  },
  addCommands() {
    return {
      setFontSize: fontSize => ({ chain }) => chain().setMark('textStyle', { fontSize }).run(),
      unsetFontSize: () => ({ chain }) => chain().setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run(),
    }
  },
})

const BugImage = Node.create({
  name: 'bugImage',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      src: { default: '' },
      alt: { default: '' },
    }
  },
  parseHTML() {
    return [{ tag: 'img[src]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['img', mergeAttributes(HTMLAttributes)]
  },
  addCommands() {
    return {
      insertBugImage: options => ({ commands }) => commands.insertContent({
        type: this.name,
        attrs: options,
      }),
    }
  },
})

const inlineImageInput = ref<HTMLInputElement | null>(null)
let syncingFromEditor = false
let syncingFromModel = false

const editor = useEditor({
  content: normalizeEditorContent(props.modelValue),
  editable: !props.readonly,
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
    BugImage,
    Placeholder.configure({
      placeholder: props.placeholder,
    }),
  ],
  editorProps: {
    attributes: {
      class: 'bug-rich-text-input',
    },
    handlePaste(view, event) {
      const files = Array.from(event.clipboardData?.files ?? [])
      const imageFiles = files.filter(file => file.type.startsWith('image/'))
      if (props.allowInlineImage && imageFiles.length) {
        event.preventDefault()
        imageFiles.forEach(insertInlineImageFile)
        return true
      }
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
    emit('update:modelValue', currentEditor.getHTML())
    queueMicrotask(() => {
      syncingFromEditor = false
    })
  },
})

watch(
  () => props.modelValue,
  () => {
    if (!syncingFromEditor) {
      syncEditorFromModel()
    }
  },
)

watch(
  () => props.readonly,
  value => editor.value?.setEditable(!value),
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

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

const panelStyle = computed(() => ({
  '--bug-rich-editor-min-height': `${props.minHeight}px`,
}))

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

function syncEditorFromModel() {
  if (!editor.value || syncingFromModel) {
    return
  }
  const normalized = normalizeEditorContent(props.modelValue)
  if (editor.value.getHTML() === normalized) {
    return
  }
  syncingFromModel = true
  editor.value.commands.setContent(normalized, { emitUpdate: false })
  queueMicrotask(() => {
    syncingFromModel = false
  })
}

function chain(editorInstance?: Editor | null) {
  return editorInstance?.chain().focus()
}

function applyHeading(value: string | number | object) {
  if (!editor.value || props.readonly) {
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
  if (!editor.value || props.readonly) {
    return
  }
  if (value === 'default') {
    editor.value.commands.unsetFontSize()
    return
  }
  editor.value.chain().focus().setFontSize(String(value)).run()
}

function clearFormatting() {
  if (props.readonly) {
    return
  }
  editor.value?.chain().focus().unsetAllMarks().clearNodes().run()
}

function clearContent() {
  if (props.readonly) {
    return
  }
  editor.value?.commands.clearContent()
  emit('update:modelValue', '')
}

function openInlineImagePicker() {
  if (!props.allowInlineImage || props.readonly) {
    return
  }
  inlineImageInput.value?.click()
}

function insertInlineImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    return
  }
  const src = URL.createObjectURL(file)
  chain(editor.value)?.insertBugImage({ src, alt: file.name }).run()
  emit('add-inline-image', { file, src })
}

function handleInlineImageChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = Array.from(input?.files ?? [])
  files.forEach(insertInlineImageFile)
  if (input) {
    input.value = ''
  }
}
</script>

<template>
  <div class="bug-rich-editor" :style="panelStyle">
    <div v-if="!readonly" class="bug-rich-editor-toolbar">
      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="撤销" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :disabled="!editor?.can().undo()" @click="chain(editor)?.undo().run()">
            <el-icon><RefreshLeft /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="恢复" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :disabled="!editor?.can().redo()" @click="chain(editor)?.redo().run()">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <span class="bug-rich-editor-toolbar-divider" />

      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="标题/正文" placement="top">
          <el-dropdown trigger="click" @command="applyHeading">
            <el-button text class="bug-rich-editor-toolbar-select">
              <span class="bug-rich-editor-toolbar-select-label">{{ currentHeadingLabel }}</span>
              <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-caret">
                <path d="m5.5 7.5 4.5 5 4.5-5" />
              </svg>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in headingOptions" :key="item.value" :command="item.value">
                  {{ item.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>

        <el-tooltip v-bind="toolbarTooltipProps" content="字号" placement="top">
          <el-dropdown trigger="click" @command="applyFontSize">
            <el-button text class="bug-rich-editor-toolbar-select bug-rich-editor-toolbar-size-select">
              <span class="bug-rich-editor-toolbar-select-label">{{ currentFontSizeLabel }}</span>
              <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-caret">
                <path d="m5.5 7.5 4.5 5 4.5-5" />
              </svg>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in fontSizeOptions" :key="item.value" :command="item.value">
                  {{ item.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>
      </div>

      <span class="bug-rich-editor-toolbar-divider" />

      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="加粗" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('bold') }" @click="chain(editor)?.toggleBold().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M6 4.5h5.2a3 3 0 0 1 0 6H6zm0 6h6a3 3 0 1 1 0 6H6z" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="斜体" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('italic') }" @click="chain(editor)?.toggleItalic().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M11.5 4.5h4M4.5 15.5h4M11 4.5l-2 11" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="下划线" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('underline') }" @click="chain(editor)?.toggleUnderline().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M5.5 4.5v4.2a4.5 4.5 0 0 0 9 0V4.5M4.5 15.5h11" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="删除线" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('strike') }" @click="chain(editor)?.toggleStrike().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M6 6.5c.4-1.4 1.8-2.2 4-2.2 2.4 0 4 .9 4 2.6 0 1.7-1.6 2.4-4 2.8-2.5.4-4 1.2-4 3 0 1.8 1.7 2.9 4.2 2.9 2.2 0 3.8-.9 4.4-2.4M4 10h12" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="高亮" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('highlight') }" @click="chain(editor)?.toggleHighlight().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M6 12.5 12.5 6l1.5 1.5L7.5 14H6zM11.7 6.8l1.5-1.5 2 2-1.5 1.5M4 15.5h12" />
            </svg>
          </el-button>
        </el-tooltip>
      </div>

      <span class="bug-rich-editor-toolbar-divider" />

      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="无序列表" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('bulletList') }" @click="chain(editor)?.toggleBulletList().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M5 6h.01M5 10h.01M5 14h.01M8 6h7M8 10h7M8 14h7" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="有序列表" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('orderedList') }" @click="chain(editor)?.toggleOrderedList().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4.5 6h1.5v4M4.2 14.5c.5-.8 1.2-1.3 2-1.3.9 0 1.5.5 1.5 1.2 0 .5-.3.9-.8 1.4l-1.6 1.4h2.6M10 6h6M10 10h6M10 14h6" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="任务列表" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('taskList') }" @click="chain(editor)?.toggleTaskList().run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4.5 5.5h3v3h-3zM5.2 7l.8.8 1.4-1.8M10 7h6M4.5 11.5h3v3h-3zM5.2 13l.8.8 1.4-1.8M10 13h6" />
            </svg>
          </el-button>
        </el-tooltip>
      </div>

      <span class="bug-rich-editor-toolbar-divider" />

      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="左对齐" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'left' }) }" @click="chain(editor)?.setTextAlign('left').run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4 5.5h11M4 9h8M4 12.5h11M4 16h7" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="居中" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'center' }) }" @click="chain(editor)?.setTextAlign('center').run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4.5 5.5h11M6 9h8M4.5 12.5h11M6.5 16h7" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="右对齐" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'right' }) }" @click="chain(editor)?.setTextAlign('right').run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M5 5.5h11M8 9h8M5 12.5h11M9 16h7" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="两端对齐" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'justify' }) }" @click="chain(editor)?.setTextAlign('justify').run()">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4 5.5h12M4 9h12M4 12.5h12M4 16h12" />
            </svg>
          </el-button>
        </el-tooltip>
      </div>

      <span class="bug-rich-editor-toolbar-divider" />

      <div class="bug-rich-editor-toolbar-group">
        <el-tooltip v-bind="toolbarTooltipProps" content="清除格式" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" @click="clearFormatting">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4.5 5.5h8M8.5 5.5v9M6.5 15.5h4M11.5 6.5l4 4M15.5 6.5l-4 4" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-if="allowInlineImage" v-bind="toolbarTooltipProps" content="插入图片" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" @click="openInlineImagePicker">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M4.5 5.5h11v9h-11zM7 12l2.1-2.2 1.8 1.9 1.1-1.2 2.5 2.5M7.2 7.5h.01" />
            </svg>
          </el-button>
        </el-tooltip>
        <el-tooltip v-bind="toolbarTooltipProps" content="清空内容" placement="top">
          <el-button text class="bug-rich-editor-toolbar-button" @click="clearContent">
            <svg viewBox="0 0 20 20" class="bug-rich-editor-toolbar-svg">
              <path d="M6 6h8M7 6V4.8h6V6M7 8v6.5M10 8v6.5M13 8v6.5M5.5 6l.7 10h7.6l.7-10" />
            </svg>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div class="bug-rich-editor-input">
      <EditorContent v-if="editor" :editor="editor" />
    </div>
    <input
      v-if="allowInlineImage"
      ref="inlineImageInput"
      type="file"
      accept="image/png,image/jpeg,image/webp"
      multiple
      class="bug-rich-editor-hidden-input"
      @change="handleInlineImageChange"
    >
  </div>
</template>

<style scoped>
.bug-rich-editor {
  border: 1px solid #d0d5dd;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.bug-rich-editor-toolbar {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-wrap: nowrap;
  padding: 6px 8px;
  border-bottom: 1px solid #eaecf0;
  background: #f8fafc;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.bug-rich-editor-toolbar-group {
  display: flex;
  align-items: center;
  gap: 0;
  flex: 0 0 auto;
}

.bug-rich-editor-toolbar-divider {
  width: 1px;
  height: 14px;
  margin: 0 1px;
  background: #d0d5dd;
  flex: 0 0 auto;
}

.bug-rich-editor-toolbar-button,
.bug-rich-editor-toolbar-select {
  height: 26px;
  min-width: 26px;
  padding: 0 5px;
  border-radius: 4px;
  color: #344054;
  flex: 0 0 auto;
}

.bug-rich-editor-toolbar-button:hover,
.bug-rich-editor-toolbar-button.is-active,
.bug-rich-editor-toolbar-select:hover {
  color: #175cd3;
  background: #eff8ff;
}

.bug-rich-editor-toolbar-size-select {
  min-width: 54px;
}

.bug-rich-editor-toolbar-select-label {
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.bug-rich-editor-toolbar-caret {
  width: 14px;
  height: 14px;
  margin-left: 4px;
  stroke: currentColor;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.bug-rich-editor-toolbar-svg {
  width: 16px;
  height: 16px;
  stroke: currentColor;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.bug-rich-editor-toolbar-button :deep(.el-icon) {
  font-size: 13px;
}

.bug-rich-editor-input {
  min-height: var(--bug-rich-editor-min-height);
  background: #fff;
}

.bug-rich-editor-input :deep(.tiptap) {
  min-height: var(--bug-rich-editor-min-height);
  padding: 14px 16px 18px;
  color: #344054;
  font-size: 13px;
  line-height: 1.8;
  outline: none;
  word-break: break-word;
}

.bug-rich-editor-input :deep(.tiptap p) {
  margin: 0 0 12px;
}

.bug-rich-editor-input :deep(.tiptap p:last-child) {
  margin-bottom: 0;
}

.bug-rich-editor-input :deep(.tiptap ul),
.bug-rich-editor-input :deep(.tiptap ol) {
  margin: 0 0 12px;
  padding-left: 20px;
}

.bug-rich-editor-input :deep(.tiptap h1),
.bug-rich-editor-input :deep(.tiptap h2),
.bug-rich-editor-input :deep(.tiptap h3),
.bug-rich-editor-input :deep(.tiptap h4),
.bug-rich-editor-input :deep(.tiptap h5),
.bug-rich-editor-input :deep(.tiptap h6) {
  margin: 0 0 12px;
  color: #101828;
  line-height: 1.5;
}

.bug-rich-editor-input :deep(.tiptap img) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 10px 0 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  object-fit: contain;
}

.bug-rich-editor-input :deep(.tiptap p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: #98a2b3;
  pointer-events: none;
  height: 0;
}

.bug-rich-editor-hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .bug-rich-editor-toolbar {
    gap: 2px;
    padding: 6px;
  }
}
</style>
