<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

type EvidenceItemId = string | number

export type EvidenceItem = {
  id: EvidenceItemId
  name: string
  previewUrl?: string | null
  removeDisabled?: boolean
}

const props = withDefaults(defineProps<{
  title: string
  count: number
  meta: string
  items: EvidenceItem[]
  addText?: string
  loading?: boolean
  disabled?: boolean
  accept?: string
}>(), {
  addText: '上传附件',
  loading: false,
  disabled: false,
  accept: '',
})

const emit = defineEmits<{
  (event: 'add-files', files: File[]): void
  (event: 'remove', id: EvidenceItemId): void
  (event: 'activate', id: EvidenceItemId): void
}>()

const uploadInput = ref<HTMLInputElement | null>(null)
const dropActive = ref(false)
const activePreviewId = ref<EvidenceItemId | null>(null)
const previewVisible = ref(false)
const previewTitle = ref('')
const previewUrl = ref('')
const previewScale = ref(1)
const previewOffset = ref({ x: 0, y: 0 })
const previewDragging = ref(false)
const previewDragOrigin = ref({ x: 0, y: 0, offsetX: 0, offsetY: 0 })

const imageItems = computed(() => props.items.filter(item => !!item.previewUrl))
const activePreviewIndex = computed(() => imageItems.value.findIndex(item => item.id === activePreviewId.value))
const canPreviewPreviousImage = computed(() => activePreviewIndex.value > 0)
const canPreviewNextImage = computed(() => (
  activePreviewIndex.value >= 0 && activePreviewIndex.value < imageItems.value.length - 1
))
const previewCounter = computed(() => {
  if (activePreviewIndex.value < 0) {
    return ''
  }
  return `${activePreviewIndex.value + 1} / ${imageItems.value.length}`
})

watch(previewVisible, (visible) => {
  if (!visible) {
    resetPreview()
    activePreviewId.value = null
    window.removeEventListener('keydown', handlePreviewKeydown)
    return
  }
  window.addEventListener('keydown', handlePreviewKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handlePreviewKeydown)
})

function openFilePicker() {
  if (props.disabled) {
    return
  }
  uploadInput.value?.click()
}

function emitFiles(files: File[]) {
  if (!files.length || props.disabled) {
    return
  }
  emit('add-files', files)
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = Array.from(input?.files ?? [])
  if (input) {
    input.value = ''
  }
  emitFiles(files)
}

function handlePaste(event: ClipboardEvent) {
  if (props.disabled) {
    return
  }
  const imageFiles = Array.from(event.clipboardData?.items ?? [])
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile())
    .filter((file): file is File => !!file && file.type.startsWith('image/'))
    .map((file, index) => new File(
      [file],
      `paste-${Date.now()}-${index + 1}.${file.type.split('/')[1] || 'png'}`,
      { type: file.type || 'image/png' },
    ))
  if (!imageFiles.length) {
    return
  }
  event.preventDefault()
  emitFiles(imageFiles)
}

function handleDragEnter() {
  if (props.disabled) {
    return
  }
  dropActive.value = true
}

function handleDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement | null
  const relatedTarget = event.relatedTarget as globalThis.Node | null
  if (currentTarget && relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  dropActive.value = false
}

function handleDrop(event: DragEvent) {
  dropActive.value = false
  if (props.disabled) {
    return
  }
  emitFiles(Array.from(event.dataTransfer?.files ?? []))
}

function previewItem(id: EvidenceItemId) {
  const item = props.items.find(current => current.id === id)
  if (!item) {
    return
  }
  if (!item.previewUrl) {
    emit('activate', id)
    return
  }
  activePreviewId.value = item.id
  previewTitle.value = item.name
  previewUrl.value = item.previewUrl
  previewVisible.value = true
}

function resetPreview() {
  previewScale.value = 1
  previewOffset.value = { x: 0, y: 0 }
  previewDragging.value = false
}

function zoomPreview(delta: number) {
  const nextScale = Math.min(4, Math.max(1, Number((previewScale.value + delta).toFixed(2))))
  previewScale.value = nextScale
  if (nextScale === 1) {
    previewOffset.value = { x: 0, y: 0 }
  }
}

function handlePreviewWheel(event: WheelEvent) {
  event.preventDefault()
  zoomPreview(event.deltaY < 0 ? 0.2 : -0.2)
}

function handlePreviewPointerDown(event: MouseEvent) {
  if (previewScale.value <= 1) {
    return
  }
  previewDragging.value = true
  previewDragOrigin.value = {
    x: event.clientX,
    y: event.clientY,
    offsetX: previewOffset.value.x,
    offsetY: previewOffset.value.y,
  }
}

function handlePreviewPointerMove(event: MouseEvent) {
  if (!previewDragging.value) {
    return
  }
  previewOffset.value = {
    x: previewDragOrigin.value.offsetX + event.clientX - previewDragOrigin.value.x,
    y: previewDragOrigin.value.offsetY + event.clientY - previewDragOrigin.value.y,
  }
}

function handlePreviewPointerUp() {
  previewDragging.value = false
}

function togglePreviewZoom() {
  if (previewScale.value > 1) {
    resetPreview()
    return
  }
  previewScale.value = 2
}

function openPreviewByOffset(offset: -1 | 1) {
  const currentIndex = activePreviewIndex.value
  if (currentIndex < 0) {
    return
  }
  const nextItem = imageItems.value[currentIndex + offset]
  if (!nextItem) {
    return
  }
  previewItem(nextItem.id)
}

function handlePreviewKeydown(event: KeyboardEvent) {
  if (!previewVisible.value) {
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    openPreviewByOffset(-1)
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    openPreviewByOffset(1)
  }
}
</script>

<template>
  <section
    class="evidence-card"
    :class="{ 'is-drop-active': dropActive }"
    tabindex="0"
    @paste="handlePaste"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="dropActive = !disabled"
    @dragleave="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <div class="evidence-header">
      <div>
        <div class="evidence-title">{{ title }}（{{ count }}）</div>
        <div class="evidence-meta">{{ meta }}</div>
      </div>
    </div>
    <input ref="uploadInput" type="file" multiple :accept="accept" style="display: none" @change="handleFileChange">
    <div class="evidence-files">
      <div v-for="item in items" :key="item.id" class="evidence-file">
        <button
          type="button"
          class="evidence-file-remove"
          :disabled="item.removeDisabled"
          @click.stop="emit('remove', item.id)"
        >
          ×
        </button>
        <div class="evidence-file-preview">
          <button
            v-if="item.previewUrl"
            type="button"
            class="evidence-thumb"
            @click="previewItem(item.id)"
          >
            <img :src="item.previewUrl" :alt="item.name" class="evidence-thumb-image">
          </button>
          <div v-else class="evidence-file-fallback">
            {{ (item.name.split('.').pop() || 'FILE').slice(0, 6).toUpperCase() }}
          </div>
        </div>
        <button type="button" class="evidence-file-trigger" @click="previewItem(item.id)">
          <span class="evidence-file-name" :title="item.name">{{ item.name }}</span>
        </button>
      </div>

      <button type="button" class="evidence-add" :disabled="disabled" @click="openFilePicker">
        <span class="evidence-add-plus">+</span>
        <span class="evidence-add-text">
          {{ loading ? '上传中...' : addText }}
        </span>
      </button>
    </div>

    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="min(960px, 92vw)"
      class="evidence-preview-dialog"
    >
      <div class="evidence-preview-toolbar">
        <div v-if="imageItems.length > 1" class="evidence-preview-nav">
          <el-button plain size="small" :disabled="!canPreviewPreviousImage" @click="openPreviewByOffset(-1)">上一张</el-button>
          <div v-if="previewCounter" class="evidence-preview-counter">{{ previewCounter }}</div>
          <el-button plain size="small" :disabled="!canPreviewNextImage" @click="openPreviewByOffset(1)">下一张</el-button>
        </div>
        <div v-else class="evidence-preview-tip">滚轮缩放，拖拽查看局部</div>
        <div class="evidence-preview-actions">
          <el-button plain size="small" class="evidence-preview-icon-button" @click="zoomPreview(-0.2)">-</el-button>
          <span class="evidence-preview-scale">{{ Math.round(previewScale * 100) }}%</span>
          <el-button plain size="small" class="evidence-preview-icon-button" @click="zoomPreview(0.2)">+</el-button>
          <el-button plain size="small" @click="resetPreview">重置</el-button>
        </div>
      </div>
      <div class="evidence-preview-shell">
        <div
          class="evidence-preview-canvas"
          :class="{ 'is-draggable': previewScale > 1, 'is-dragging': previewDragging }"
          @wheel="handlePreviewWheel"
          @mousedown="handlePreviewPointerDown"
          @mousemove="handlePreviewPointerMove"
          @mouseup="handlePreviewPointerUp"
          @mouseleave="handlePreviewPointerUp"
          @dblclick="togglePreviewZoom"
        >
          <img
            :src="previewUrl"
            :alt="previewTitle"
            class="evidence-preview-image"
            :style="{ transform: `translate(${previewOffset.x}px, ${previewOffset.y}px) scale(${previewScale})` }"
          >
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.evidence-card {
  margin-top: 4px;
  padding: 16px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.evidence-card.is-drop-active {
  border-color: rgba(64, 158, 255, 0.55);
  background: rgba(239, 246, 255, 0.72);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.evidence-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.evidence-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.evidence-meta {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.evidence-files {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 104px));
  gap: 10px;
  margin-top: 14px;
  padding-top: 3px;
  align-items: start;
}

.evidence-file {
  position: relative;
  display: grid;
  grid-template-rows: 104px auto;
  gap: 6px;
  width: 104px;
  background: transparent;
  transition: transform 0.18s ease;
}

.evidence-file:hover {
  transform: translateY(-1px);
}

.evidence-file-remove {
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
  transition: opacity 0.18s ease, background 0.18s ease;
}

.evidence-file:hover .evidence-file-remove,
.evidence-file:focus-within .evidence-file-remove {
  opacity: 1;
  pointer-events: auto;
}

.evidence-file-remove:disabled {
  cursor: wait;
  opacity: 0.7;
  pointer-events: auto;
}

.evidence-file-preview {
  display: flex;
  min-height: 0;
  width: 104px;
  height: 104px;
}

.evidence-file-fallback,
.evidence-thumb {
  width: 100%;
  height: 100%;
}

.evidence-thumb {
  padding: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  background: #f8fafc;
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4);
}

.evidence-thumb-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.evidence-file-fallback {
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

.evidence-file-trigger {
  display: grid;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.evidence-file-name {
  font-size: 11px;
  line-height: 1.45;
  color: #98a2b3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.18s ease;
}

.evidence-file:hover .evidence-file-name,
.evidence-file:focus-within .evidence-file-name {
  color: #667085;
}

.evidence-add {
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
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.evidence-add:hover {
  border-color: #98a2b3;
  background: #f8fafc;
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px rgba(208, 213, 221, 0.45);
}

.evidence-add:disabled {
  cursor: wait;
  opacity: 0.7;
}

.evidence-add-plus {
  font-size: 28px;
  line-height: 1;
  font-weight: 500;
  color: #475467;
}

.evidence-add-text {
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
  color: #667085;
}

.evidence-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 12px;
}

.evidence-preview-nav,
.evidence-preview-actions {
  display: flex;
  align-items: center;
}

.evidence-preview-nav {
  gap: 8px;
}

.evidence-preview-actions {
  gap: 6px;
  margin-left: auto;
}

.evidence-preview-tip,
.evidence-preview-scale {
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.evidence-preview-counter {
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

:global(.evidence-preview-dialog .el-button.is-plain) {
  min-width: 32px;
  height: 32px;
  padding: 0 12px;
  border-color: rgba(208, 213, 221, 0.9);
  border-radius: 8px;
  background: #ffffff;
  color: #475467;
}

:global(.evidence-preview-dialog .el-button.is-plain:hover),
:global(.evidence-preview-dialog .el-button.is-plain:focus-visible) {
  border-color: rgba(127, 86, 217, 0.45);
  background: #f8f5ff;
  color: #6941c6;
}

:global(.evidence-preview-dialog .el-button.is-disabled) {
  border-color: rgba(208, 213, 221, 0.7);
  background: #f8fafc;
  color: #98a2b3;
}

:global(.evidence-preview-dialog .evidence-preview-icon-button) {
  min-width: 32px;
  padding: 0 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.evidence-preview-shell {
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

.evidence-preview-canvas {
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

.evidence-preview-canvas.is-draggable {
  cursor: grab;
}

.evidence-preview-canvas.is-dragging {
  cursor: grabbing;
}

.evidence-preview-image {
  display: block;
  max-width: min(100%, 1000px);
  max-height: 100%;
  object-fit: contain;
  transform-origin: center center;
  transition: transform 0.16s ease;
  user-select: none;
  -webkit-user-drag: none;
}
</style>
