import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { attachmentApi } from '../api'

// 上传限制：单个 10MB，单条消息最多 5 个，支持图片与文档
export const MAX_ATTACHMENT_SIZE_MB = 10
export const MAX_ATTACHMENT_COUNT = 5
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
// 文档扩展名白名单：部分客户端对文档统一上传为 application/octet-stream，按后缀兜底
export const ALLOWED_DOCUMENT_EXTENSIONS = ['pdf', 'docx', 'txt']
// 上传控件 accept：图片 MIME + 文档后缀
export const UPLOAD_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,.pdf,.docx,.txt'

// 附件已失效（服务端重启或被删）时的占位图
export const IMAGE_FALLBACK = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="100%" height="100%" fill="#f0f2f5"/><text x="50%" y="52%" text-anchor="middle" dominant-baseline="middle" font-size="12" fill="#999">图片已失效</text></svg>`
)

let uid = 0

const getExtension = (name) => {
  const index = (name || '').lastIndexOf('.')
  return index < 0 ? '' : name.slice(index + 1).toLowerCase()
}

export const isImageFile = (file) => ALLOWED_IMAGE_TYPES.includes(file.type)

export const isDocumentFile = (file) => ALLOWED_DOCUMENT_EXTENSIONS.includes(getExtension(file.name))

/**
 * 聊天附件上传复用逻辑（ChatView / AgentChatView 共用）：
 * 选文件/粘贴图片即上传，维护发送前的附件暂存区；发送时输出 fileIds，失败时可恢复重试
 *
 * @param {import('vue').Ref<boolean>|null} enabled 附件上传能力开关（跟随 Agent 配置），为 null 时视为始终开启
 */
export function useAttachmentUpload(enabled = null) {
  // 暂存区条目：{ key, fileId, fileName, url, isImage, uploading }
  const pendingAttachments = ref([])

  const uploadingCount = computed(() => pendingAttachments.value.filter(att => att.uploading).length)

  // 是否存在"可随消息发送"的附件（全部上传完成）
  const hasReadyAttachments = computed(() =>
    pendingAttachments.value.length > 0 && uploadingCount.value === 0
  )

  const isEnabled = () => !enabled || enabled.value

  const clear = () => {
    pendingAttachments.value = []
  }

  const validateFile = (file) => {
    if (!isImageFile(file) && !isDocumentFile(file)) {
      message.warning('不支持的附件格式，仅支持图片（JPG/PNG/GIF/WebP）与文档（PDF/Word/TXT）')
      return false
    }
    if (file.size > MAX_ATTACHMENT_SIZE_MB * 1024 * 1024) {
      message.warning(`附件大小不能超过 ${MAX_ATTACHMENT_SIZE_MB}MB`)
      return false
    }
    return true
  }

  /**
   * 新增附件并立即上传：图片生成本地预览，文档仅记录文件名；上传期间显示 loading
   */
  const addFiles = async (files) => {
    if (!isEnabled()) {
      message.warning('当前 Agent 未开启附件上传功能')
      return
    }

    const fileList = Array.from(files || [])
    if (fileList.length === 0) return

    const remain = MAX_ATTACHMENT_COUNT - pendingAttachments.value.length
    if (remain <= 0) {
      message.warning(`最多上传 ${MAX_ATTACHMENT_COUNT} 个附件`)
      return
    }
    if (fileList.length > remain) {
      message.warning(`最多上传 ${MAX_ATTACHMENT_COUNT} 个附件，已保留前 ${remain} 个`)
    }

    for (const file of fileList.slice(0, remain)) {
      if (!validateFile(file)) continue

      const isImage = isImageFile(file)
      // 用 reactive 包装：上传完成后对 item 的属性修改才能触发视图更新
      const item = reactive({
        key: `att_${Date.now()}_${uid++}`,
        fileId: null,
        fileName: file.name || (isImage ? '粘贴的图片' : '附件'),
        url: isImage ? URL.createObjectURL(file) : '',
        isImage,
        uploading: true
      })
      pendingAttachments.value.push(item)

      const localUrl = item.url
      try {
        const response = await attachmentApi.uploadAttachment(file)
        if (response.data?.success && response.data.fileId) {
          item.fileId = response.data.fileId
          item.url = attachmentApi.attachmentUrl(item.fileId)
          item.uploading = false
          if (localUrl) URL.revokeObjectURL(localUrl)
        } else {
          throw new Error(response.data?.message || '上传失败')
        }
      } catch (error) {
        // 上传失败：从暂存区移除并提示
        const index = pendingAttachments.value.indexOf(item)
        if (index >= 0) pendingAttachments.value.splice(index, 1)
        if (localUrl) URL.revokeObjectURL(localUrl)
        message.error(`附件上传失败: ${error.response?.data?.message || error.message}`)
      }
    }
  }

  /**
   * 移除暂存附件：同步删除服务端附件以释放内存
   */
  const removeAttachment = (item) => {
    if (item.uploading) return
    const index = pendingAttachments.value.indexOf(item)
    if (index >= 0) pendingAttachments.value.splice(index, 1)
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
  const getFileIds = () => pendingAttachments.value.filter(att => att.fileId).map(att => att.fileId)

  /**
   * 发送失败后恢复附件到暂存区（附件仍在服务端，可直接重试）：
   * 传入发送前暂存条目的快照，保留文件名与图片/文档类型
   */
  const restoreAttachments = (items) => {
    for (const item of items || []) {
      pendingAttachments.value.push({ ...item, key: `att_${Date.now()}_${uid++}` })
    }
  }

  return {
    pendingAttachments,
    uploadingCount,
    hasReadyAttachments,
    addFiles,
    removeAttachment,
    handlePaste,
    handleUploadSelect,
    getFileIds,
    restoreAttachments,
    clear
  }
}
