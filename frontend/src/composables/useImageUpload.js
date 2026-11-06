import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { attachmentApi } from '../api'

// 上传限制：单张 10MB，单条消息最多 5 张，仅支持图片
export const MAX_IMAGE_SIZE_MB = 10
export const MAX_IMAGE_COUNT = 5
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']

// 附件已失效（服务端重启或被删）时的占位图
export const IMAGE_FALLBACK = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="100%" height="100%" fill="#f0f2f5"/><text x="50%" y="52%" text-anchor="middle" dominant-baseline="middle" font-size="12" fill="#999">图片已失效</text></svg>`
)

let uid = 0

/**
 * 聊天图片上传复用逻辑（ChatView / AgentChatView 共用）：
 * 选图/粘贴即上传，维护发送前的图片暂存区；发送时输出 fileIds，失败时可恢复重试
 *
 * @param {import('vue').Ref<boolean>|null} enabled 图片上传能力开关（跟随 Agent 配置），为 null 时视为始终开启
 */
export function useImageUpload(enabled = null) {
  // 暂存区条目：{ key, fileId, fileName, url, uploading }
  const pendingImages = ref([])

  const uploadingCount = computed(() => pendingImages.value.filter(img => img.uploading).length)

  // 是否存在"可随消息发送"的图片（全部上传完成）
  const hasReadyImages = computed(() =>
    pendingImages.value.length > 0 && uploadingCount.value === 0
  )

  const isEnabled = () => !enabled || enabled.value

  const clear = () => {
    pendingImages.value = []
  }

  const validateFile = (file) => {
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      message.warning('不支持的图片格式，仅支持 JPG/PNG/GIF/WebP')
      return false
    }
    if (file.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
      message.warning(`图片大小不能超过 ${MAX_IMAGE_SIZE_MB}MB`)
      return false
    }
    return true
  }

  /**
   * 新增图片并立即上传：上传期间显示 loading，完成后替换为服务端地址
   */
  const addFiles = async (files) => {
    if (!isEnabled()) {
      message.warning('当前 Agent 未开启图片上传功能')
      return
    }

    const fileList = Array.from(files || [])
    if (fileList.length === 0) return

    const remain = MAX_IMAGE_COUNT - pendingImages.value.length
    if (remain <= 0) {
      message.warning(`最多上传 ${MAX_IMAGE_COUNT} 张图片`)
      return
    }
    if (fileList.length > remain) {
      message.warning(`最多上传 ${MAX_IMAGE_COUNT} 张图片，已保留前 ${remain} 张`)
    }

    for (const file of fileList.slice(0, remain)) {
      if (!validateFile(file)) continue

      // 用 reactive 包装：上传完成后对 item 的属性修改才能触发视图更新
      const item = reactive({
        key: `img_${Date.now()}_${uid++}`,
        fileId: null,
        fileName: file.name || '粘贴的图片',
        url: URL.createObjectURL(file),
        uploading: true
      })
      pendingImages.value.push(item)

      const localUrl = item.url
      try {
        const response = await attachmentApi.uploadImage(file)
        if (response.data?.success && response.data.fileId) {
          item.fileId = response.data.fileId
          item.url = attachmentApi.attachmentUrl(item.fileId)
          item.uploading = false
          URL.revokeObjectURL(localUrl)
        } else {
          throw new Error(response.data?.message || '上传失败')
        }
      } catch (error) {
        // 上传失败：从暂存区移除并提示
        const index = pendingImages.value.indexOf(item)
        if (index >= 0) pendingImages.value.splice(index, 1)
        URL.revokeObjectURL(localUrl)
        message.error(`图片上传失败: ${error.response?.data?.message || error.message}`)
      }
    }
  }

  /**
   * 移除暂存图片：同步删除服务端附件以释放内存
   */
  const removeImage = (item) => {
    if (item.uploading) return
    const index = pendingImages.value.indexOf(item)
    if (index >= 0) pendingImages.value.splice(index, 1)
    if (item.fileId) {
      attachmentApi.deleteAttachment(item.fileId).catch(() => {})
    }
  }

  /**
   * 粘贴处理：提取剪贴板中的图片并上传；纯图片粘贴时阻止二进制内容进入输入框
   */
  const handlePaste = (event) => {
    if (!isEnabled()) return

    const items = event.clipboardData?.items
    if (!items) return

    const imageFiles = []
    let hasText = false
    for (const item of Array.from(items)) {
      if (item.kind === 'file' && item.type.startsWith('image/')) {
        const file = item.getAsFile()
        if (file) imageFiles.push(file)
      } else if (item.kind === 'string') {
        hasText = true
      }
    }

    if (imageFiles.length > 0) {
      if (!hasText) event.preventDefault()
      addFiles(imageFiles)
    }
  }

  /**
   * 上传控件选择文件入口（before-upload 返回 false 阻止自动上传）
   */
  const handleUploadSelect = (file) => {
    addFiles([file])
    return false
  }

  /**
   * 取暂存区中可发送的 fileIds（保持添加顺序）
   */
  const getFileIds = () => pendingImages.value.filter(img => img.fileId).map(img => img.fileId)

  /**
   * 发送失败后恢复图片到暂存区（附件仍在服务端，可直接重试）
   */
  const restoreImages = (fileIds) => {
    for (const fileId of fileIds || []) {
      pendingImages.value.push({
        key: `img_${Date.now()}_${uid++}`,
        fileId,
        fileName: '图片',
        url: attachmentApi.attachmentUrl(fileId),
        uploading: false
      })
    }
  }

  return {
    pendingImages,
    uploadingCount,
    hasReadyImages,
    addFiles,
    removeImage,
    handlePaste,
    handleUploadSelect,
    getFileIds,
    restoreImages,
    clear
  }
}
