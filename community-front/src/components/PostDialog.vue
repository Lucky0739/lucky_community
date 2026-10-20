<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { request, requestPage, uploadFile } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  /** 传入则进入编辑模式，调用 PUT /post/{postId} */
  postId: { type: String, default: '' },
  /** 详情拉取失败时（如审核中不可见）用已有字段预填 */
  initial: { type: Object, default: null },
})

const emit = defineEmits(['close', 'need-login', 'success'])
const auth = useAuthStore()

const editPostId = computed(() => idStr(props.postId))
const isEdit = computed(() => editPostId.value !== '' && editPostId.value !== '0')

const MAX_IMAGES = 9
const MAX_TITLE = 64
const MAX_CONTENT = 5000
const EMOJIS = ['😀', '😂', '🥰', '😭', '👍', '🎉', '🔥', '❤️']

const tab = ref('post')
const bars = ref([])
const barsError = ref('')
const showEmoji = ref(false)
const showImages = ref(false)
const uploading = ref(false)
const recognizing = ref(false)
const submitting = ref(false)
const loadingPost = ref(false)
const error = ref('')
const fileInput = ref(null)
const barNameReadonly = ref('')

const form = reactive({
  barId: '',
  title: '',
  content: '',
  images: [],
})

onMounted(() => {
  loadBars()
  if (isEdit.value) {
    loadPostForEdit()
  }
})

watch(editPostId, (id) => {
  if (id && id !== '0') {
    loadPostForEdit()
  } else {
    resetForm()
  }
})

function resetForm() {
  form.barId = ''
  form.title = ''
  form.content = ''
  form.images = []
  barNameReadonly.value = ''
  error.value = ''
}

function applyInitial(initial) {
  if (!initial) return
  if (initial.barId != null) form.barId = idStr(initial.barId)
  if (initial.barName) barNameReadonly.value = initial.barName
  if (initial.title) form.title = String(initial.title).slice(0, MAX_TITLE)
  if (initial.content) form.content = String(initial.content).slice(0, MAX_CONTENT)
  if (Array.isArray(initial.images) && initial.images.length) {
    form.images = [...initial.images]
    showImages.value = true
  }
}

async function loadPostForEdit() {
  if (!isEdit.value) return
  if (!requireLogin()) return
  loadingPost.value = true
  error.value = ''
  resetForm()
  try {
    const detail = await request(`/post/${editPostId.value}`, {
      token: auth.accessToken,
    })
    form.barId = idStr(detail.barId)
    barNameReadonly.value = detail.barName || ''
    form.title = String(detail.title || '').slice(0, MAX_TITLE)
    form.content = String(detail.content || '').slice(0, MAX_CONTENT)
    form.images = Array.isArray(detail.images) ? [...detail.images] : []
    if (form.images.length) showImages.value = true
  } catch (e) {
    applyInitial(props.initial)
    if (!form.title && !form.content) {
      error.value = e.message || '帖子加载失败，暂时无法编辑'
    } else {
      error.value = '帖子未公开，已用当前页面内容预填；保存后将重新审核'
    }
  } finally {
    loadingPost.value = false
  }
}

async function loadBars() {
  barsError.value = ''
  if (!auth.loggedIn) {
    barsError.value = '登录后才能选择贴吧'
    return
  }
  try {
    const { records } = await requestPage('/bar/page?pageNum=1&pageSize=100', {
      token: auth.accessToken,
    })
    bars.value = records || []
    if (!bars.value.length) {
      barsError.value = '还没有可发布的贴吧'
    }
  } catch (e) {
    barsError.value = e.message || '贴吧列表加载失败'
  }
}

function requireLogin() {
  if (auth.loggedIn) {
    return true
  }
  emit('need-login')
  return false
}

function insertEmoji(emoji) {
  form.content = (form.content + emoji).slice(0, MAX_CONTENT)
}

function openImagePicker() {
  showImages.value = true
  if (!requireLogin()) {
    return
  }
  fileInput.value?.click()
}

async function onFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) {
    return
  }
  if (!requireLogin()) {
    return
  }
  const room = MAX_IMAGES - form.images.length
  if (room <= 0) {
    error.value = `最多上传 ${MAX_IMAGES} 张图片`
    return
  }
  error.value = ''
  uploading.value = true
  try {
    for (const file of files.slice(0, room)) {
      if (!/^image\/(jpeg|png|gif|webp)$/.test(file.type)) {
        throw new Error('只支持 JPG、PNG、GIF、WebP')
      }
      if (file.size > 5 * 1024 * 1024) {
        throw new Error('单张图片不能超过 5MB')
      }
      const url = await uploadFile('/post/image', file, auth.accessToken)
      form.images.push(url)
    }
    if (files.length > room) {
      error.value = `最多上传 ${MAX_IMAGES} 张，多出的没有上传`
    }
    await fillFromImages()
  } catch (e) {
    error.value = e.message || '图片上传失败'
  } finally {
    uploading.value = false
  }
}

/** 标题或正文还空着时，用已上传图片补上，已填写的不覆盖 */
async function fillFromImages() {
  if (!form.images.length || (form.title.trim() && form.content.trim())) {
    return
  }
  recognizing.value = true
  try {
    const draft = await request('/post/draft', {
      method: 'POST',
      token: auth.accessToken,
      body: { imageUrls: form.images.slice(0, 3) },
    })
    if (!form.title.trim() && draft?.title) {
      form.title = String(draft.title).slice(0, MAX_TITLE)
    }
    if (!form.content.trim() && draft?.content) {
      form.content = String(draft.content).slice(0, MAX_CONTENT)
    }
  } catch (e) {
    error.value = e.message || '图片识别失败'
  } finally {
    recognizing.value = false
  }
}

function removeImage(index) {
  form.images.splice(index, 1)
}

async function submit() {
  error.value = ''
  if (!requireLogin()) {
    return
  }
  if (!isEdit.value && !form.barId) {
    error.value = '请选择贴吧'
    return
  }
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title) {
    error.value = '请输入标题'
    return
  }
  if (title.length > MAX_TITLE) {
    error.value = `标题不能超过 ${MAX_TITLE} 个字`
    return
  }
  if (!content) {
    error.value = '请输入正文'
    return
  }
  if (content.length > MAX_CONTENT) {
    error.value = `正文不能超过 ${MAX_CONTENT} 个字`
    return
  }
  submitting.value = true
  try {
    const body = { title, content, images: form.images }
    if (isEdit.value) {
      await request(`/post/${editPostId.value}`, {
        method: 'PUT',
        token: auth.accessToken,
        body,
      })
      emit('success', { mode: 'update', postId: editPostId.value })
    } else {
      await request('/post/create', {
        method: 'POST',
        token: auth.accessToken,
        body: {
          barId: idStr(form.barId),
          ...body,
        },
      })
      emit('success', { mode: 'create' })
    }
    emit('close')
  } catch (e) {
    error.value = e.message || (isEdit.value ? '保存失败' : '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <form class="dialog" @submit.prevent="submit">
      <header class="head">
        <div class="tabs">
          <button type="button" :class="{ on: tab === 'post' }" @click="tab = 'post'">{{ isEdit ? '编辑帖子' : '发帖' }}</button>
          <button v-if="!isEdit" type="button" :class="{ on: tab === 'video' }" @click="tab = 'video'">发视频</button>
        </div>
        <button type="button" class="close" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <p v-if="tab === 'video'" class="video-tip">视频发帖能力尚未开放，请使用图文发帖。</p>

      <template v-else>
        <div class="row">
          <span class="label">发布到吧</span>
          <select v-if="!isEdit" v-model="form.barId">
            <option value="" disabled>选择吧</option>
            <option v-for="bar in bars" :key="bar.barId" :value="idStr(bar.barId)">{{ bar.name }}</option>
          </select>
          <span v-else class="bar-readonly">{{ barNameReadonly || '当前贴吧' }}</span>
        </div>
        <p v-if="!isEdit && barsError" class="hint">{{ barsError }}</p>
        <p v-if="loadingPost" class="hint">加载帖子…</p>

        <input
          v-model="form.title"
          class="title"
          type="text"
          :maxlength="MAX_TITLE"
          placeholder="必填，请输入帖子标题（最多64个字）"
        />

        <textarea
          v-model="form.content"
          :maxlength="MAX_CONTENT"
          placeholder="请输入正文（最多5000字）"
        />

        <div v-if="showEmoji" class="emojis">
          <button v-for="emoji in EMOJIS" :key="emoji" type="button" @click="insertEmoji(emoji)">{{ emoji }}</button>
        </div>

        <div v-if="showImages" class="images">
          <button
            v-for="(url, index) in form.images"
            :key="url"
            type="button"
            class="thumb"
            @click="removeImage(index)"
          >
            <img :src="url" alt="" />
            <span>移除</span>
          </button>
          <button v-if="form.images.length < MAX_IMAGES" type="button" class="add" @click="openImagePicker">
            {{ uploading ? '上传中…' : recognizing ? '识别中…' : '＋ 添加图片' }}
          </button>
          <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple hidden @change="onFiles" />
        </div>

        <div class="tools">
          <button type="button" @click="showEmoji = !showEmoji">☺ 表情</button>
          <button type="button" @click="showImages = !showImages">🖼 图片</button>
        </div>

        <p v-if="error" class="error">{{ error }}</p>

        <div class="foot">
          <button class="submit" type="submit" :disabled="submitting || uploading || recognizing || loadingPost">
            {{ submitting ? (isEdit ? '保存中…' : '发布中…') : (isEdit ? '保存' : '发布') }}
          </button>
        </div>
      </template>
    </form>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: rgba(17, 24, 39, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.dialog {
  width: 720px;
  max-width: 100%;
  max-height: calc(100vh - 48px);
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  padding: 18px 28px 22px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 18px;
}
.tabs {
  display: flex;
  gap: 28px;
}
.tabs button {
  border: none;
  background: transparent;
  font-size: 18px;
  padding: 8px 0 12px;
  cursor: pointer;
  color: #6b7280;
}
.tabs button.on {
  color: #111827;
  font-weight: 700;
  box-shadow: inset 0 -3px 0 #3b6cff;
}
.close {
  border: none;
  background: transparent;
  font-size: 28px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
}
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}
.label {
  font-weight: 700;
}
.bar-readonly {
  font-size: 14px;
  color: #374151;
  padding: 8px 12px;
  background: #f3f4f6;
  border-radius: 10px;
}
select,
.title,
textarea {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8f9fb;
  font-size: 14px;
  color: #111827;
}
select {
  height: 40px;
  min-width: 220px;
  padding: 0 12px;
}
.title {
  width: 100%;
  height: 48px;
  margin: 12px 0;
  padding: 0 14px;
}
textarea {
  width: 100%;
  min-height: 220px;
  padding: 14px;
  resize: vertical;
  line-height: 1.6;
}
.title:focus,
textarea:focus,
select:focus {
  outline: 2px solid #dbeafe;
  border-color: #93c5fd;
  background: #fff;
}
.tools {
  display: flex;
  gap: 28px;
  margin: 14px 0;
}
.tools button,
.emojis button,
.add {
  border: none;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 15px;
}
.emojis {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.emojis button {
  font-size: 20px;
}
.images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}
.thumb,
.add {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  border: 1px dashed #d1d5db;
  background: #f9fafb;
  position: relative;
  overflow: hidden;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb span {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  padding: 2px 0;
}
.hint,
.error,
.video-tip {
  margin: 8px 0;
  font-size: 13px;
}
.hint,
.video-tip {
  color: #6b7280;
}
.error {
  color: #dc2626;
}
.video-tip {
  min-height: 180px;
  display: grid;
  place-items: center;
  font-size: 16px;
}
.foot {
  display: flex;
  justify-content: flex-end;
}
.submit {
  height: 40px;
  padding: 0 28px;
  border: none;
  border-radius: 20px;
  background: #3b6cff;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>
