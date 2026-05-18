<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, ref, watch } from 'vue'
import type { Component } from 'vue'
import {
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
import { Editor, EditorContent, NodeViewWrapper, VueNodeViewRenderer, useEditor } from '@tiptap/vue-3'
import type { NodeViewProps } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Underline from '@tiptap/extension-underline'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { Extension, Node, mergeAttributes } from '@tiptap/core'
import { TextStyle } from '@tiptap/extension-text-style'
import type { UserItem, WorkspaceItem } from '../types/api'

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    fontSize: {
      setFontSize: (size: string) => ReturnType
      unsetFontSize: () => ReturnType
    }
    bugImage: {
      insertBugImage: (options: { src: string; alt?: string }) => ReturnType
    }
  }
}

type BugEditorFormModel = {
  workspaceCode: string
  title: string
  priority: string
  severity: string
  assigneeId: number | null
  tags: string[]
  description: string
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
            renderHTML: attributes => (attributes.fontSize ? { style: `font-size: ${attributes.fontSize}` } : {}),
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

const BugImageNodeView = defineComponent({
  name: 'BugImageNodeView',
  props: {
    node: {
      type: Object,
      required: true,
    },
    selected: {
      type: Boolean,
      default: false,
    },
    deleteNode: {
      type: Function,
      required: true,
    },
  },
  setup(props) {
    const imageSrc = computed(() => String((props.node as { attrs?: Record<string, unknown> }).attrs?.src ?? ''))
    const imageAlt = computed(() => String((props.node as { attrs?: Record<string, unknown> }).attrs?.alt ?? '缺陷图片'))

    function stopEvent(event: Event) {
      event.preventDefault()
      event.stopPropagation()
    }

    function openPreview(event: Event) {
      stopEvent(event)
      const previewWindow = window.open('', '_blank', 'noopener,noreferrer')
      if (!previewWindow) {
        return
      }
      previewWindow.document.title = imageAlt.value
      previewWindow.document.body.style.margin = '0'
      previewWindow.document.body.style.background = '#fff'
      const image = previewWindow.document.createElement('img')
      image.src = imageSrc.value
      image.alt = imageAlt.value
      image.style.display = 'block'
      image.style.maxWidth = 'none'
      image.style.maxHeight = 'none'
      previewWindow.document.body.appendChild(image)
    }

    function removeImage(event: Event) {
      stopEvent(event)
      props.deleteNode()
    }

    return () => h(NodeViewWrapper, {
      class: ['bug-editor-image-node', { 'is-selected': props.selected }],
      'data-drag-handle': '',
    }, () => [
      h('div', {
        class: 'bug-editor-image-tools',
        contenteditable: 'false',
      }, [
        h('button', {
          type: 'button',
          class: 'bug-editor-image-tool',
          title: '预览图片',
          onMousedown: stopEvent,
          onClick: openPreview,
        }, [
          h('svg', { viewBox: '0 0 20 20' }, [
            h('path', { d: 'M7.5 4.5H5.8A1.3 1.3 0 0 0 4.5 5.8v8.4c0 .7.6 1.3 1.3 1.3h8.4c.7 0 1.3-.6 1.3-1.3v-1.7M10 4.5h5.5V10M9.5 10.5l5.4-5.4' }),
          ]),
        ]),
        h('button', {
          type: 'button',
          class: 'bug-editor-image-tool',
          title: '删除图片',
          onMousedown: stopEvent,
          onClick: removeImage,
        }, [
          h('svg', { viewBox: '0 0 20 20' }, [
            h('path', { d: 'M6 6h8M7 6V4.8h6V6M7 8v6.5M10 8v6.5M13 8v6.5M5.5 6l.7 10h7.6l.7-10' }),
          ]),
        ]),
      ]),
      h('img', {
        src: imageSrc.value,
        alt: imageAlt.value,
        draggable: 'true',
      }),
    ])
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
      src: {
        default: null,
      },
      alt: {
        default: null,
      },
      title: {
        default: null,
      },
    }
  },
  parseHTML() {
    return [{ tag: 'img[src]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['img', mergeAttributes(HTMLAttributes)]
  },
  addNodeView() {
    return VueNodeViewRenderer(BugImageNodeView as Component<NodeViewProps>)
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

const props = withDefaults(defineProps<{
  form: BugEditorFormModel
  saving: boolean
  users: UserItem[]
  pendingFiles?: PendingBugFile[]
  showWorkspace?: boolean
  workspaces?: WorkspaceItem[]
  pageMode?: boolean
}>(), {
  pendingFiles: () => [],
  showWorkspace: false,
  workspaces: () => [],
  pageMode: false,
})

const emit = defineEmits<{
  (event: 'add-files', files: File[]): void
  (event: 'remove-file', id: string): void
  (event: 'add-inline-image', payload: { file: File; src: string }): void
}>()

const inlineImageInput = ref<HTMLInputElement | null>(null)
const uploadInput = ref<HTMLInputElement | null>(null)

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
  get: () => 'ASSIGNED',
  set: () => {},
})

const statusOptions = [{ label: '已指派', value: 'ASSIGNED' }]

const toolbarTooltipProps = {
  showAfter: 200,
  hideAfter: 0,
  enterable: false,
}

const evidenceDropActive = ref(false)
const activeEvidencePreviewId = ref<string | null>(null)
const evidencePreviewVisible = ref(false)
const evidencePreviewTitle = ref('')
const evidencePreviewUrl = ref('')
const evidencePreviewScale = ref(1)
const evidencePreviewOffset = ref({ x: 0, y: 0 })
const evidencePreviewDragging = ref(false)
const evidencePreviewDragOrigin = ref({ x: 0, y: 0, offsetX: 0, offsetY: 0 })
const evidenceImageFiles = computed(() => props.pendingFiles.filter(item => !!item.previewUrl))
const activeEvidencePreviewIndex = computed(() => (
  evidenceImageFiles.value.findIndex(item => item.id === activeEvidencePreviewId.value)
))
const canPreviewPreviousEvidenceImage = computed(() => activeEvidencePreviewIndex.value > 0)
const canPreviewNextEvidenceImage = computed(() => (
  activeEvidencePreviewIndex.value >= 0 && activeEvidencePreviewIndex.value < evidenceImageFiles.value.length - 1
))
const evidencePreviewCounter = computed(() => {
  if (activeEvidencePreviewIndex.value < 0) {
    return ''
  }
  return `${activeEvidencePreviewIndex.value + 1} / ${evidenceImageFiles.value.length}`
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
    BugImage,
    Placeholder.configure({
      placeholder: '请输入缺陷描述',
    }),
  ],
  editorProps: {
    attributes: {
      class: 'bug-editor-rich-input',
    },
    handlePaste(view, event) {
      const files = Array.from(event.clipboardData?.files ?? [])
      const imageFiles = files.filter(file => file.type.startsWith('image/'))
      if (imageFiles.length) {
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
    props.form.description = currentEditor.getHTML()
    queueMicrotask(() => {
      syncingFromEditor = false
    })
  },
})

watch(
  () => props.form.description,
  () => {
    if (!syncingFromEditor) {
      syncEditorFromForm()
    }
  },
)

watch(evidencePreviewVisible, (visible) => {
  if (visible) {
    window.addEventListener('keydown', handleEvidencePreviewKeydown)
    return
  }
  window.removeEventListener('keydown', handleEvidencePreviewKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEvidencePreviewKeydown)
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

function openInlineImagePicker() {
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

function openAttachmentPicker() {
  uploadInput.value?.click()
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = Array.from(input?.files ?? [])
  if (input) {
    input.value = ''
  }
  if (!files.length) {
    return
  }
  emit('add-files', files)
}

function handleEvidencePaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.items ?? [])
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile())
    .filter((item): item is File => !!item && item.type.startsWith('image/'))
    .map((file, index) => new File([file], `bug-screenshot-${Date.now()}-${index + 1}.png`, { type: file.type || 'image/png' }))
  if (!files.length || props.saving) {
    return
  }
  event.preventDefault()
  emit('add-files', files)
}

function handleEvidenceDragEnter() {
  if (props.saving) {
    return
  }
  evidenceDropActive.value = true
}

function handleEvidenceDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement | null
  const relatedTarget = event.relatedTarget as globalThis.Node | null
  if (currentTarget && relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  evidenceDropActive.value = false
}

function handleEvidenceDrop(event: DragEvent) {
  evidenceDropActive.value = false
  if (props.saving) {
    return
  }
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (!files.length) {
    return
  }
  emit('add-files', files)
}

function previewEvidenceFile(id: string) {
  const target = props.pendingFiles.find(item => item.id === id)
  if (!target) {
    return
  }
  if (!target.previewUrl) {
    return
  }
  resetEvidencePreview()
  activeEvidencePreviewId.value = target.id
  evidencePreviewTitle.value = target.name
  evidencePreviewUrl.value = target.previewUrl
  evidencePreviewVisible.value = true
}

function resetEvidencePreview() {
  evidencePreviewScale.value = 1
  evidencePreviewOffset.value = { x: 0, y: 0 }
  evidencePreviewDragging.value = false
}

function zoomEvidencePreview(delta: number) {
  const nextScale = Math.min(4, Math.max(1, Number((evidencePreviewScale.value + delta).toFixed(2))))
  evidencePreviewScale.value = nextScale
  if (nextScale === 1) {
    evidencePreviewOffset.value = { x: 0, y: 0 }
  }
}

function handleEvidencePreviewWheel(event: WheelEvent) {
  event.preventDefault()
  zoomEvidencePreview(event.deltaY < 0 ? 0.2 : -0.2)
}

function handleEvidencePreviewPointerDown(event: MouseEvent) {
  if (evidencePreviewScale.value <= 1) {
    return
  }
  evidencePreviewDragging.value = true
  evidencePreviewDragOrigin.value = {
    x: event.clientX,
    y: event.clientY,
    offsetX: evidencePreviewOffset.value.x,
    offsetY: evidencePreviewOffset.value.y,
  }
}

function handleEvidencePreviewPointerMove(event: MouseEvent) {
  if (!evidencePreviewDragging.value) {
    return
  }
  evidencePreviewOffset.value = {
    x: evidencePreviewDragOrigin.value.offsetX + event.clientX - evidencePreviewDragOrigin.value.x,
    y: evidencePreviewDragOrigin.value.offsetY + event.clientY - evidencePreviewDragOrigin.value.y,
  }
}

function handleEvidencePreviewPointerUp() {
  evidencePreviewDragging.value = false
}

function toggleEvidencePreviewZoom() {
  if (evidencePreviewScale.value > 1) {
    resetEvidencePreview()
    return
  }
  evidencePreviewScale.value = 2
}

function openEvidencePreviewByOffset(offset: -1 | 1) {
  const currentIndex = activeEvidencePreviewIndex.value
  if (currentIndex < 0) {
    return
  }
  const nextItem = evidenceImageFiles.value[currentIndex + offset]
  if (!nextItem) {
    return
  }
  previewEvidenceFile(nextItem.id)
}

function handleEvidencePreviewKeydown(event: KeyboardEvent) {
  if (!evidencePreviewVisible.value) {
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    openEvidencePreviewByOffset(-1)
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    openEvidencePreviewByOffset(1)
  }
}

</script>

<template>
  <el-form label-position="top" class="bug-editor-form" :class="{ 'is-page-mode': pageMode }">
    <div class="bug-editor-layout">
      <div class="bug-editor-main">
        <el-form-item label="缺陷名称" required class="bug-editor-form-item bug-editor-main-item">
          <el-input v-model="form.title" placeholder="请输入缺陷名称" />
        </el-form-item>

        <el-form-item label="缺陷内容" required class="bug-editor-form-item bug-editor-main-item">
          <div class="bug-editor-content-panel">
            <div class="bug-editor-toolbar">
              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="撤销" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :disabled="!editor?.can().undo()" @click="chain(editor)?.undo().run()">
                    <el-icon><RefreshLeft /></el-icon>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="恢复" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :disabled="!editor?.can().redo()" @click="chain(editor)?.redo().run()">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>

              <span class="bug-editor-toolbar-divider"></span>

              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="标题/正文" placement="top">
                  <el-dropdown trigger="click" @command="applyHeading">
                    <el-button text class="bug-editor-toolbar-select">
                      <span class="bug-editor-toolbar-select-label">{{ currentHeadingLabel }}</span>
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-caret">
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
                    <el-button text class="bug-editor-toolbar-select bug-editor-toolbar-size-select">
                      <span class="bug-editor-toolbar-select-label">{{ currentFontSizeLabel }}</span>
                      <svg viewBox="0 0 20 20" class="bug-editor-toolbar-caret">
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

              <span class="bug-editor-toolbar-divider"></span>

              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="加粗" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('bold') }" @click="chain(editor)?.toggleBold().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M6 4.5h5.2a3 3 0 0 1 0 6H6zm0 6h6a3 3 0 1 1 0 6H6z" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="斜体" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('italic') }" @click="chain(editor)?.toggleItalic().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M11.5 4.5h4M4.5 15.5h4M11 4.5l-2 11" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="下划线" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('underline') }" @click="chain(editor)?.toggleUnderline().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M5.5 4.5v4.2a4.5 4.5 0 0 0 9 0V4.5M4.5 15.5h11" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="删除线" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('strike') }" @click="chain(editor)?.toggleStrike().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M6 6.5c.4-1.4 1.8-2.2 4-2.2 2.4 0 4 .9 4 2.6 0 1.7-1.6 2.4-4 2.8-2.5.4-4 1.2-4 3 0 1.8 1.7 2.9 4.2 2.9 2.2 0 3.8-.9 4.4-2.4M4 10h12" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="高亮" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('highlight') }" @click="chain(editor)?.toggleHighlight().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M6 12.5 12.5 6l1.5 1.5L7.5 14H6zM11.7 6.8l1.5-1.5 2 2-1.5 1.5M4 15.5h12" />
                    </svg>
                  </el-button>
                </el-tooltip>
              </div>

              <span class="bug-editor-toolbar-divider"></span>

              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="无序列表" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('bulletList') }" @click="chain(editor)?.toggleBulletList().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M5 6h.01M5 10h.01M5 14h.01M8 6h7M8 10h7M8 14h7" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="有序列表" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('orderedList') }" @click="chain(editor)?.toggleOrderedList().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4.5 6h1.5v4M4.2 14.5c.5-.8 1.2-1.3 2-1.3.9 0 1.5.5 1.5 1.2 0 .5-.3.9-.8 1.4l-1.6 1.4h2.6M10 6h6M10 10h6M10 14h6" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="任务列表" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive('taskList') }" @click="chain(editor)?.toggleTaskList().run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4.5 5.5h3v3h-3zM5.2 7l.8.8 1.4-1.8M10 7h6M4.5 11.5h3v3h-3zM5.2 13l.8.8 1.4-1.8M10 13h6" />
                    </svg>
                  </el-button>
                </el-tooltip>
              </div>

              <span class="bug-editor-toolbar-divider"></span>

              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="左对齐" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'left' }) }" @click="chain(editor)?.setTextAlign('left').run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4 5.5h11M4 9h8M4 12.5h11M4 16h7" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="居中" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'center' }) }" @click="chain(editor)?.setTextAlign('center').run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4.5 5.5h11M6 9h8M4.5 12.5h11M6.5 16h7" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="右对齐" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'right' }) }" @click="chain(editor)?.setTextAlign('right').run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M5 5.5h11M8 9h8M5 12.5h11M9 16h7" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="两端对齐" placement="top">
                  <el-button text class="bug-editor-toolbar-button" :class="{ 'is-active': editor?.isActive({ textAlign: 'justify' }) }" @click="chain(editor)?.setTextAlign('justify').run()">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4 5.5h12M4 9h12M4 12.5h12M4 16h12" />
                    </svg>
                  </el-button>
                </el-tooltip>
              </div>

              <span class="bug-editor-toolbar-divider"></span>

              <div class="bug-editor-toolbar-group">
                <el-tooltip v-bind="toolbarTooltipProps" content="清除格式" placement="top">
                  <el-button text class="bug-editor-toolbar-button" @click="clearFormatting">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4.5 5.5h8M8.5 5.5v9M6.5 15.5h4M11.5 6.5l4 4M15.5 6.5l-4 4" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="插入图片" placement="top">
                  <el-button text class="bug-editor-toolbar-button" @click="openInlineImagePicker">
                    <svg viewBox="0 0 20 20" class="bug-editor-toolbar-svg">
                      <path d="M4.5 5.5h11v9h-11zM7 12l2.1-2.2 1.8 1.9 1.1-1.2 2.5 2.5M7.2 7.5h.01" />
                    </svg>
                  </el-button>
                </el-tooltip>

                <el-tooltip v-bind="toolbarTooltipProps" content="清空内容" placement="top">
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

        <section
          class="bug-editor-evidence-card"
          :class="{ 'is-drop-active': evidenceDropActive }"
          tabindex="0"
          @paste="handleEvidencePaste"
          @dragenter.prevent="handleEvidenceDragEnter"
          @dragover.prevent="evidenceDropActive = !saving"
          @dragleave="handleEvidenceDragLeave"
          @drop.prevent="handleEvidenceDrop"
        >
          <div class="bug-editor-evidence-header">
            <div>
              <div class="bug-editor-section-title">执行截图（{{ pendingFiles.length }}）</div>
              <div class="bug-editor-evidence-meta">点击加号上传，或在此区域按 Ctrl+V 粘贴图片</div>
            </div>
          </div>
          <input ref="uploadInput" type="file" multiple style="display: none" @change="handleFileChange">
          <input ref="inlineImageInput" type="file" accept="image/png,image/jpeg,image/webp" multiple style="display: none" @change="handleInlineImageChange">
          <div class="bug-editor-evidence-files">
            <div v-for="item in pendingFiles" :key="item.id" class="bug-editor-evidence-file">
              <button type="button" class="bug-editor-evidence-file-remove" @click.stop="emit('remove-file', item.id)">
                ×
              </button>
              <div class="bug-editor-evidence-file-preview">
                <button
                  v-if="item.previewUrl"
                  type="button"
                  class="bug-editor-evidence-thumb"
                  @click="previewEvidenceFile(item.id)"
                >
                  <img :src="item.previewUrl" :alt="item.name" class="bug-editor-evidence-thumb-image">
                </button>
                <div v-else class="bug-editor-evidence-file-fallback">
                  {{ (item.name.split('.').pop() || 'FILE').slice(0, 6).toUpperCase() }}
                </div>
              </div>
              <button type="button" class="bug-editor-evidence-file-trigger" @click="previewEvidenceFile(item.id)">
                <span class="bug-editor-evidence-file-name" :title="item.name">{{ item.name }}</span>
              </button>
            </div>

            <button type="button" class="bug-editor-evidence-add" :disabled="saving" @click="openAttachmentPicker">
              <span class="bug-editor-evidence-add-plus">+</span>
              <span class="bug-editor-evidence-add-text">
                {{ saving ? '上传中...' : '上传截图' }}
              </span>
            </button>
          </div>
        </section>
      </div>

      <aside class="bug-editor-side">
        <el-form-item v-if="showWorkspace" label="所属空间" required class="bug-editor-form-item bug-editor-side-item">
          <el-select v-model="form.workspaceCode" placeholder="请选择所属空间">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="处理人" required class="bug-editor-form-item bug-editor-side-item">
          <el-select v-model="form.assigneeId" placeholder="请选择">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" required class="bug-editor-form-item bug-editor-side-item">
          <el-select v-model="bugStatus" placeholder="请选择" disabled>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" required class="bug-editor-form-item bug-editor-side-item">
          <el-select v-model="form.priority" placeholder="请选择">
            <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="严重程度" required class="bug-editor-form-item bug-editor-side-item">
          <el-select v-model="form.severity" placeholder="请选择">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
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
            :teleported="false"
            popper-class="bug-editor-tag-popper"
            placeholder="输入内容后回车可直接添加标签"
          />
        </el-form-item>
      </aside>
    </div>

    <el-dialog
      v-model="evidencePreviewVisible"
      :title="evidencePreviewTitle"
      width="min(960px, 92vw)"
      class="bug-editor-evidence-preview-dialog"
    >
      <div class="bug-editor-evidence-preview-toolbar">
        <div v-if="evidenceImageFiles.length > 1" class="bug-editor-evidence-preview-nav">
          <el-button plain size="small" :disabled="!canPreviewPreviousEvidenceImage" @click="openEvidencePreviewByOffset(-1)">上一张</el-button>
          <div v-if="evidencePreviewCounter" class="bug-editor-evidence-preview-counter">{{ evidencePreviewCounter }}</div>
          <el-button plain size="small" :disabled="!canPreviewNextEvidenceImage" @click="openEvidencePreviewByOffset(1)">下一张</el-button>
        </div>
        <div v-else class="bug-editor-evidence-preview-tip">滚轮缩放，拖拽查看局部</div>
        <div class="bug-editor-evidence-preview-actions">
          <el-button plain size="small" class="bug-editor-evidence-preview-icon-button" @click="zoomEvidencePreview(-0.2)">-</el-button>
          <span class="bug-editor-evidence-preview-scale">{{ Math.round(evidencePreviewScale * 100) }}%</span>
          <el-button plain size="small" class="bug-editor-evidence-preview-icon-button" @click="zoomEvidencePreview(0.2)">+</el-button>
          <el-button plain size="small" @click="resetEvidencePreview">重置</el-button>
        </div>
      </div>
      <div class="bug-editor-evidence-preview-shell">
        <div
          class="bug-editor-evidence-preview-canvas"
          :class="{ 'is-draggable': evidencePreviewScale > 1, 'is-dragging': evidencePreviewDragging }"
          @wheel="handleEvidencePreviewWheel"
          @mousedown="handleEvidencePreviewPointerDown"
          @mousemove="handleEvidencePreviewPointerMove"
          @mouseup="handleEvidencePreviewPointerUp"
          @mouseleave="handleEvidencePreviewPointerUp"
          @dblclick="toggleEvidencePreviewZoom"
        >
          <img
            :src="evidencePreviewUrl"
            :alt="evidencePreviewTitle"
            class="bug-editor-evidence-preview-image"
            :style="{
              transform: `translate(${evidencePreviewOffset.x}px, ${evidencePreviewOffset.y}px) scale(${evidencePreviewScale})`,
            }"
          >
        </div>
      </div>
    </el-dialog>
  </el-form>
</template>

<style scoped>
.bug-editor-form {
  min-height: 100%;
}

.bug-editor-form :deep(.el-input__wrapper),
.bug-editor-form :deep(.el-select__wrapper) {
  border-radius: 2px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.bug-editor-form :deep(.el-select__wrapper) {
  min-height: 34px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__wrapper) {
  align-items: center;
  min-height: 34px;
  padding: 0 10px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__selection) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__selected-item) {
  margin: 0;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-tag) {
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

.bug-editor-form :deep(.bug-editor-tag-input .el-tag .el-tag__content) {
  font-size: 12px;
  line-height: 22px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-tag .el-tag__close) {
  margin-left: 4px;
  color: #667085;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__input-wrapper) {
  margin: 0;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__input) {
  min-width: 96px;
  margin: 0;
  font-size: 12px;
  line-height: 24px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__placeholder) {
  color: #98a2b3;
  font-size: 12px;
  line-height: 32px;
}

.bug-editor-form :deep(.bug-editor-tag-input .el-select__caret),
.bug-editor-form :deep(.bug-editor-tag-input .el-select__suffix) {
  display: none;
}

.bug-editor-form :deep(.bug-editor-tag-popper) {
  display: none !important;
}

.bug-editor-form :deep(.el-input__inner),
.bug-editor-form :deep(.el-select__placeholder) {
  font-size: 12px;
}

.bug-editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  min-height: calc(100vh - 126px);
}

.bug-editor-form.is-page-mode .bug-editor-layout {
  grid-template-columns: minmax(0, 1fr) 322px;
  min-height: auto;
}

.bug-editor-main {
  min-width: 0;
  padding: 14px 14px 20px;
}

.bug-editor-form.is-page-mode .bug-editor-main {
  padding: 34px 28px 32px 34px;
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

.bug-editor-form.is-page-mode .bug-editor-side {
  padding: 34px 34px 32px 16px;
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

.bug-editor-form.is-page-mode .bug-editor-description-input :deep(.tiptap) {
  min-height: 320px;
  font-size: 14px;
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

.bug-editor-description-input :deep(.bug-editor-image-node) {
  position: relative;
  width: 100%;
  max-width: 100%;
  margin: 10px 0 14px;
  border: 1px solid transparent;
  border-radius: 6px;
  line-height: 0;
}

.bug-editor-description-input :deep(.bug-editor-image-node.is-selected) {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.22);
}

.bug-editor-description-input :deep(.bug-editor-image-node img) {
  display: block;
  width: 100%;
  height: auto;
  max-width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  object-fit: contain;
}

.bug-editor-description-input :deep(.bug-editor-image-tools) {
  position: absolute;
  top: -50px;
  left: 50%;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.18);
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%) translateY(4px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.bug-editor-description-input :deep(.bug-editor-image-node.is-selected .bug-editor-image-tools),
.bug-editor-description-input :deep(.bug-editor-image-node:hover .bug-editor-image-tools) {
  opacity: 1;
  pointer-events: auto;
  transform: translateX(-50%) translateY(0);
}

.bug-editor-description-input :deep(.bug-editor-image-tool) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 2px;
  background: transparent;
  color: #4a5568;
  cursor: pointer;
}

.bug-editor-description-input :deep(.bug-editor-image-tool:hover) {
  background: #f5f7fa;
  color: #1f2d3d;
}

.bug-editor-description-input :deep(.bug-editor-image-tool svg) {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
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
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.bug-editor-evidence-card.is-drop-active {
  border-color: rgba(64, 158, 255, 0.55);
  background: rgba(239, 246, 255, 0.72);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
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

.bug-editor-evidence-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 12px;
}

.bug-editor-evidence-preview-nav,
.bug-editor-evidence-preview-actions {
  display: flex;
  align-items: center;
}

.bug-editor-evidence-preview-nav {
  gap: 8px;
}

.bug-editor-evidence-preview-actions {
  gap: 6px;
  margin-left: auto;
}

.bug-editor-evidence-preview-tip,
.bug-editor-evidence-preview-scale {
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.bug-editor-evidence-preview-counter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid rgba(208, 213, 221, 0.9);
  border-radius: 8px;
  background: #f2f4f7;
}

:global(.bug-editor-evidence-preview-dialog .el-button.is-plain) {
  min-width: 32px;
  height: 32px;
  padding: 0 12px;
  border-color: rgba(208, 213, 221, 0.9);
  border-radius: 8px;
  background: #ffffff;
  color: #475467;
}

:global(.bug-editor-evidence-preview-dialog .el-button.is-plain:hover),
:global(.bug-editor-evidence-preview-dialog .el-button.is-plain:focus-visible) {
  border-color: rgba(127, 86, 217, 0.45);
  background: #f8f5ff;
  color: #6941c6;
}

:global(.bug-editor-evidence-preview-dialog .el-button.is-disabled) {
  border-color: rgba(208, 213, 221, 0.7);
  background: #f8fafc;
  color: #98a2b3;
}

:global(.bug-editor-evidence-preview-dialog .bug-editor-evidence-preview-icon-button) {
  min-width: 32px;
  padding: 0 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.bug-editor-evidence-preview-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  height: min(68vh, 640px);
  max-height: 68vh;
  overflow: hidden;
  border-radius: 12px;
  background: #f8fafc;
}

.bug-editor-evidence-preview-canvas {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  max-height: 100%;
  overflow: hidden;
  cursor: zoom-in;
}

.bug-editor-evidence-preview-canvas.is-draggable {
  cursor: grab;
}

.bug-editor-evidence-preview-canvas.is-dragging {
  cursor: grabbing;
}

.bug-editor-evidence-preview-image {
  display: block;
  max-width: min(100%, 1000px);
  max-height: 100%;
  object-fit: contain;
  transform-origin: center center;
  transition: transform 0.16s ease;
  user-select: none;
  -webkit-user-drag: none;
}

@media (max-width: 1280px) {
  .bug-editor-layout,
  .bug-editor-form.is-page-mode .bug-editor-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .bug-editor-side,
  .bug-editor-form.is-page-mode .bug-editor-side {
    padding: 14px;
    border-left: 0;
    border-top: 1px solid #ebeef5;
  }

  .bug-editor-form.is-page-mode .bug-editor-main {
    padding: 24px;
  }
}
</style>
