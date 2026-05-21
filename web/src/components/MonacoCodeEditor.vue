<template>
  <div class="ms-monaco-editor">
    <div v-if="showToolbar" class="ms-monaco-editor__toolbar">
      <button
        v-if="showFormatButton"
        type="button"
        class="ms-monaco-editor__format"
        @click="formatDocument"
      >
        格式化
      </button>
    </div>
    <div ref="containerRef" class="ms-monaco-editor__body" :style="{ height: bodyHeight }"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api'
import 'monaco-editor/esm/vs/language/json/monaco.contribution'
import 'monaco-editor/esm/vs/basic-languages/xml/xml.contribution'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

type EditorLanguage = 'json' | 'xml' | 'text'
type EditorWordWrap = 'off' | 'on' | 'wordWrapColumn' | 'bounded'

const props = withDefaults(defineProps<{
  modelValue: string
  language: EditorLanguage
  height?: string
  readOnly?: boolean
  showFormatButton?: boolean
  fitContent?: boolean
  wordWrap?: EditorWordWrap
}>(), {
  height: '500px',
  readOnly: false,
  showFormatButton: true,
  fitContent: false,
  wordWrap: 'on',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const bodyHeight = ref(props.height)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let suppressModelSync = false

const showToolbar = computed(() => props.showFormatButton && !props.readOnly)

function mapLanguage(language: EditorLanguage) {
  if (language === 'text') {
    return 'plaintext'
  }
  return language
}

function tryFormatXml(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }
  const compact = trimmed.replace(/>\s+</g, '><')
  const segments = compact.replace(/(>)(<)(\/*)/g, '$1\n$2$3').split('\n')
  let indent = 0
  return segments
    .map((segment) => {
      const line = segment.trim()
      if (!line) {
        return ''
      }
      if (/^<\//.test(line)) {
        indent = Math.max(indent - 1, 0)
      }
      const prefix = '  '.repeat(indent)
      const rendered = `${prefix}${line}`
      if (/^<[^!?/][^>]*[^/]?>$/.test(line) && !/<\/[^>]+>$/.test(line)) {
        indent += 1
      }
      return rendered
    })
    .filter(Boolean)
    .join('\n')
}

async function formatDocument() {
  if (!editor) {
    return
  }
  const value = editor.getValue()
  if (!value.trim()) {
    return
  }
  if (props.language === 'xml') {
    const formatted = tryFormatXml(value)
    suppressModelSync = true
    editor.setValue(formatted)
    suppressModelSync = false
    emit('update:modelValue', formatted)
    emit('change', formatted)
    return
  }
  if (props.language === 'json') {
    if (props.readOnly) {
      editor.updateOptions({ readOnly: false })
    }
    await editor.getAction('editor.action.formatDocument')?.run()
    if (props.readOnly) {
      editor.updateOptions({ readOnly: true })
    }
    const formatted = editor.getValue()
    emit('update:modelValue', formatted)
    emit('change', formatted)
    return
  }
  if (props.language === 'text') {
    return
  }
  if (props.readOnly) {
    editor.updateOptions({ readOnly: false })
  }
  await editor.getAction('editor.action.formatDocument')?.run()
  if (props.readOnly) {
    editor.updateOptions({ readOnly: true })
  }
}

function syncEditorHeight() {
  if (!props.fitContent || !editor) {
    return
  }
  bodyHeight.value = `${Math.max(editor.getContentHeight(), 120)}px`
  editor.layout()
}

function createEditor() {
  if (!containerRef.value) {
    return
  }
  bodyHeight.value = props.height
  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: mapLanguage(props.language),
    theme: 'vs',
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: false },
    contextmenu: !props.readOnly,
    lineNumbersMinChars: 3,
    lineDecorationsWidth: 0,
    tabSize: 2,
    scrollBeyondLastLine: false,
    wordWrap: props.wordWrap,
    roundedSelection: false,
    renderLineHighlight: 'line',
    scrollbar: {
      alwaysConsumeMouseWheel: false,
      useShadows: false,
      verticalScrollbarSize: 10,
      horizontalScrollbarSize: 10,
    },
    padding: {
      top: 12,
      bottom: 12,
    },
  })

  editor.getModel()?.setEOL(monaco.editor.EndOfLineSequence.LF)
  if (props.fitContent) {
    editor.onDidContentSizeChange(() => {
      syncEditorHeight()
    })
    syncEditorHeight()
  }
  if (props.readOnly) {
    void formatDocument()
  }
  editor.onDidChangeModelContent(() => {
    if (!editor || suppressModelSync) {
      return
    }
    const value = editor.getValue()
    emit('update:modelValue', value)
    emit('change', value)
  })
}

watch(
  () => props.modelValue,
  (value) => {
    if (!editor) {
      return
    }
    if (editor.getValue() === value) {
      return
    }
    suppressModelSync = true
    editor.setValue(value)
    suppressModelSync = false
    syncEditorHeight()
  },
)

watch(
  () => props.language,
  (language) => {
    const model = editor?.getModel()
    if (!model) {
      return
    }
    monaco.editor.setModelLanguage(model, mapLanguage(language))
    syncEditorHeight()
  },
)

watch(
  () => props.readOnly,
  (readOnly) => {
    editor?.updateOptions({
      readOnly,
      contextmenu: !readOnly,
    })
    syncEditorHeight()
  },
)

watch(
  () => props.height,
  (height) => {
    if (!props.fitContent) {
      bodyHeight.value = height
    }
  },
)

onMounted(() => {
  const globalWithMonaco = globalThis as typeof globalThis & {
    MonacoEnvironment?: {
      getWorker: (_: string, label: string) => Worker
    }
  }
  globalWithMonaco.MonacoEnvironment = {
    getWorker(_: string, label: string) {
      if (label === 'json') {
        return new jsonWorker()
      }
      if (label === 'css' || label === 'scss' || label === 'less') {
        return new cssWorker()
      }
      if (label === 'html' || label === 'handlebars' || label === 'razor') {
        return new htmlWorker()
      }
      if (label === 'typescript' || label === 'javascript') {
        return new tsWorker()
      }
      return new editorWorker()
    },
  }
  createEditor()
})

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.ms-monaco-editor {
  box-sizing: border-box;
  padding: 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;
}

.ms-monaco-editor__toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 0 0 8px;
  background: #fff;
}

.ms-monaco-editor__format {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #1d2129;
  font-size: 12px;
  cursor: pointer;
}

.ms-monaco-editor__format:hover {
  border-color: #c9cdd4;
  color: #165dff;
  background: #f7f8fa;
}

.ms-monaco-editor__body {
  min-height: 300px;
}

.ms-monaco-editor__body :deep(.monaco-editor),
.ms-monaco-editor__body :deep(.overflow-guard) {
  border-radius: 0;
}
</style>
