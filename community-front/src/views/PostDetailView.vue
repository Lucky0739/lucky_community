<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'
import LoginDialog from '@/components/LoginDialog.vue'
import CommentSection from '@/components/CommentSection.vue'
import PostDialog from '@/components/PostDialog.vue'
import iconLike from '@/icons/dianzan.png'
import iconUnlike from '@/icons/quxiaodianzan.png'
import iconFavorite from '@/icons/shoucang.png'
import iconUnfavorite from '@/icons/quxiaoshoucang.png'
import iconComment from '@/icons/pinglunxiao.png'
import iconBack from '@/icons/fanhui.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const post = ref(null)
const loading = ref(false)
const error = ref('')
const showLogin = ref(false)
const showEdit = ref(false)
const deleting = ref(false)

const viewerIsAuthor = computed(() => {
  if (post.value?.viewerIsAuthor === true) return true
  return auth.loggedIn && idStr(auth.userId) === idStr(post.value?.userId)
})

const editInitial = computed(() => {
  if (!post.value) return null
  return {
    barId: post.value.barId,
    barName: post.value.barName,
    title: post.value.title,
    content: post.value.content,
    images: post.value.images || [],
  }
})

const POLL_INTERVAL_MS = 3000
const POLL_TIMEOUT_MS = 90000
const MAX_CONSECUTIVE_POLL_ERRORS = 3

/** 作者待审核期间的轮询状态：idle | pending | rejected | failed | timeout */
const moderation = ref({ state: 'idle', analysisStatus: null, message: '' })
let pollTimer = null
let pollDeadline = 0
let pollPostId = ''
let pollSeq = 0
let loadSeq = 0
let pollErrors = 0

function readSummary() {
  const summary = history.state?.summary
  if (!summary || idStr(summary.postId) !== idStr(route.params.postId)) return null
  return { ...summary }
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
  pollPostId = ''
  pollSeq += 1
}

async function fetchModerationStatus(postId) {
  try {
    const data = await request(`/post/${postId}/moderation-status`, {
      token: auth.accessToken,
    })
    // data === null 表示当前用户不是作者或帖子不存在
    return { status: data }
  } catch (e) {
    return { error: e }
  }
}

function applyStatus(status) {
  if (!status) {
    moderation.value = { state: 'failed', analysisStatus: null, message: '无法获取审核状态，请刷新重试' }
    return false
  }
  if (status.visible) {
    moderation.value = { state: 'idle', analysisStatus: status.analysisStatus, message: '' }
    return false
  }
  if (status.analysisStatus === 3) {
    moderation.value = { state: 'failed', analysisStatus: 3, message: 'AI 审核失败，帖子暂未公开' }
    return false
  }
  if (status.analysisStatus === 2) {
    moderation.value = { state: 'rejected', analysisStatus: 2, message: '该帖未通过 AI 审核，未公开发布' }
    return false
  }
  moderation.value = {
    state: 'pending',
    analysisStatus: status.analysisStatus,
    message: 'AI 审核中，通过后将自动展示相似帖子',
  }
  return true
}

function startPolling(postId) {
  stopPolling()
  const seq = pollSeq
  pollPostId = postId
  pollDeadline = Date.now() + POLL_TIMEOUT_MS
  pollErrors = 0

  const tick = async () => {
    pollTimer = null
    if (seq !== pollSeq || pollPostId !== postId || !auth.loggedIn) return
    if (idStr(route.params.postId) !== postId) return

    if (Date.now() > pollDeadline) {
      moderation.value = { state: 'timeout', analysisStatus: null, message: '审核时间较长，请稍后重新检查' }
      stopPolling()
      return
    }

    const result = await fetchModerationStatus(postId)
    if (seq !== pollSeq || pollPostId !== postId) return

    if (result.error) {
      pollErrors += 1
      if (pollErrors >= MAX_CONSECUTIVE_POLL_ERRORS) {
        moderation.value = { state: 'failed', analysisStatus: null, message: '审核状态查询失败，请稍后重试' }
        stopPolling()
        return
      }
      pollTimer = setTimeout(tick, POLL_INTERVAL_MS)
      return
    }

    pollErrors = 0
    const keepPolling = applyStatus(result.status)
    if (!keepPolling) {
      stopPolling()
      if (result.status?.visible) {
        // 审核已通过：重新拉详情，作者分支会带回 similarPosts
        await load()
      }
      return
    }
    pollTimer = setTimeout(tick, POLL_INTERVAL_MS)
  }

  // 刚在 load() 里已经查过一次状态，等一个间隔再开始轮询，避免立刻重复请求
  pollTimer = setTimeout(tick, POLL_INTERVAL_MS)
}

function retryNow() {
  error.value = ''
  load()
}

async function load() {
  const postId = idStr(route.params.postId)
  if (!postId) return
  stopPolling()
  const seq = ++loadSeq
  const summary = readSummary()
  post.value = summary || {
    postId,
    title: '',
    content: '',
    images: [],
    likeCount: 0,
    commentCount: 0,
    favoriteCount: 0,
  }
  moderation.value = { state: 'idle', analysisStatus: null, message: '' }
  error.value = ''
  if (!auth.loggedIn) return
  loading.value = true
  try {
    const detail = await request(`/post/${postId}`, { token: auth.accessToken })
    if (seq !== loadSeq) return
    post.value = {
      ...post.value,
      ...detail,
      images: detail.images?.length ? detail.images : post.value.images || [],
      content: detail.content || post.value.content || '',
    }
  } catch (e) {
    if (seq !== loadSeq) return
    // 审核通过前详情对所有人都是 404；作者用状态接口区分“审核中/未通过/失败”
    const result = await fetchModerationStatus(postId)
    if (seq !== loadSeq) return
    if (result.error || !result.status || result.status.visible) {
      error.value = e.message || '帖子加载失败'
      return
    }
    if (applyStatus(result.status)) {
      startPolling(postId)
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function openSimilar(item) {
  const postId = idStr(item?.postId)
  if (!postId || postId === '0') return
  router.push({
    name: 'post',
    params: { postId },
    state: {
      summary: {
        postId,
        userId: idStr(item.userId),
        nickName: item.nickName || '',
        icon: item.icon || '',
        barName: item.barName || '',
        title: item.title || '',
        content: item.contentPreview || '',
        images: item.coverImage ? [item.coverImage] : [],
        likeCount: item.likeCount ?? 0,
        commentCount: item.commentCount ?? 0,
        favoriteCount: item.favoriteCount ?? 0,
        createTime: item.createTime || '',
      },
    },
  })
}

async function toggleLike() {
  if (!post.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const liked = post.value.liked === true
  const postId = idStr(post.value.postId)
  try {
    await request(`/thumb/${postId}`, {
      method: liked ? 'DELETE' : 'POST',
      token: auth.accessToken,
    })
    const delta = liked ? -1 : 1
    post.value = {
      ...post.value,
      liked: !liked,
      likeCount: Math.max(0, (Number(post.value.likeCount) || 0) + delta),
    }
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

async function toggleFavorite() {
  if (!post.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const favorited = post.value.favorited === true
  const postId = idStr(post.value.postId)
  try {
    await request(`/favorite/${postId}`, {
      method: favorited ? 'DELETE' : 'POST',
      token: auth.accessToken,
    })
    const delta = favorited ? -1 : 1
    post.value = {
      ...post.value,
      favorited: !favorited,
      favoriteCount: Math.max(0, (Number(post.value.favoriteCount) || 0) + delta),
    }
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function openUser() {
  const userId = idStr(post.value?.userId)
  if (!userId || userId === '0') return
  router.push({ name: 'user', params: { userId } })
}

function scrollToComments() {
  nextTick(() => {
    document.getElementById('post-comments')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function onCountChange(delta) {
  if (!post.value) return
  post.value = {
    ...post.value,
    commentCount: Math.max(0, (Number(post.value.commentCount) || 0) + delta),
  }
}

function openEdit() {
  if (!viewerIsAuthor.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  showEdit.value = true
}

function onEditSuccess() {
  showEdit.value = false
  load()
}

async function deletePost() {
  if (!post.value || !viewerIsAuthor.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const postId = idStr(post.value.postId)
  if (!postId) return
  if (!window.confirm('确定删除这篇帖子？删除后不可恢复。')) return
  deleting.value = true
  error.value = ''
  try {
    await request(`/post/${postId}`, {
      method: 'DELETE',
      token: auth.accessToken,
    })
    router.push({ name: 'home' })
  } catch (e) {
    error.value = e.message || '删除失败'
  } finally {
    deleting.value = false
  }
}

onMounted(load)
watch(() => route.params.postId, () => load())
watch(() => auth.loggedIn, (loggedIn) => {
  if (!loggedIn) stopPolling()
  load()
})
onBeforeUnmount(stopPolling)
watch(
  () => [route.query.comments, post.value?.postId],
  () => {
    if (route.query.comments === '1' && post.value) scrollToComments()
  },
)
</script>

<template>
  <div class="page">
    <header class="topbar">
      <button type="button" class="back" aria-label="返回首页" @click="router.push({ name: 'home' })">
        <img :src="iconBack" alt="" />
        首页
      </button>
      <span class="title">{{ post?.title || '帖子' }}</span>
      <div v-if="viewerIsAuthor" class="owner-actions">
        <button type="button" class="owner-btn" @click="openEdit">编辑</button>
        <button type="button" class="owner-btn danger" :disabled="deleting" @click="deletePost">
          {{ deleting ? '删除中…' : '删除' }}
        </button>
      </div>
    </header>

    <main class="wrap">
      <p v-if="error" class="banner error">{{ error }}</p>
      <p v-if="moderation.state === 'pending'" class="banner pending">{{ moderation.message }}</p>
      <p v-else-if="moderation.state === 'rejected'" class="banner rejected">{{ moderation.message }}</p>
      <p v-else-if="moderation.state === 'failed'" class="banner error">
        {{ moderation.message }}
        <button type="button" class="banner-retry" @click="retryNow">重新检查</button>
      </p>
      <p v-else-if="moderation.state === 'timeout'" class="banner">
        {{ moderation.message }}
        <button type="button" class="banner-retry" @click="retryNow">重新检查</button>
      </p>
      <p v-if="loading && !post?.title" class="banner">加载中…</p>

      <article v-if="post" class="sheet">
        <div class="author" @click="openUser">
          <span class="avatar">
            <img v-if="post.icon" :src="post.icon" alt="" />
            <span v-else>{{ (post.nickName || '用').slice(0, 1) }}</span>
          </span>
          <div>
            <div class="name">{{ post.nickName || '用户' }}</div>
            <div class="sub">
              <span v-if="post.barName">{{ post.barName }}</span>
              <span v-if="post.createTime">{{ formatTime(post.createTime) }}</span>
            </div>
          </div>
        </div>
        <h1>{{ post.title }}</h1>
        <p v-if="post.content" class="content">{{ post.content }}</p>
        <p v-else-if="!auth.loggedIn" class="login-hint">登录后查看全文</p>
        <img v-for="(url, index) in post.images || []" :key="index" class="photo" :src="url" alt="" />

        <div class="actions">
          <button type="button" :class="{ on: post.liked }" @click="toggleLike">
            <img :src="post.liked ? iconUnlike : iconLike" alt="" />
            点赞 {{ post.likeCount ?? 0 }}
          </button>
          <button type="button" :class="{ on: post.favorited }" @click="toggleFavorite">
            <img :src="post.favorited ? iconUnfavorite : iconFavorite" alt="" />
            收藏 {{ post.favoriteCount ?? 0 }}
          </button>
          <button type="button" class="comment-count" @click="scrollToComments">
            <img :src="iconComment" alt="" />
            评论 {{ post.commentCount ?? 0 }}
          </button>
        </div>

        <section v-if="post.viewerIsAuthor === true && post.similarPosts?.length" class="similar">
          <h2>AI 发现的相似帖子</h2>
          <button
            v-for="item in post.similarPosts"
            :key="idStr(item.postId)"
            type="button"
            class="similar-item"
            @click="openSimilar(item)"
          >
            <img v-if="item.coverImage" class="similar-cover" :src="item.coverImage" alt="" />
            <span class="similar-body">
              <span class="similar-title">{{ item.title || '无标题' }}</span>
              <span v-if="item.contentPreview" class="similar-preview">{{ item.contentPreview }}</span>
              <span class="similar-meta">
                {{ item.nickName || '用户' }}
                <template v-if="item.barName"> · {{ item.barName }}</template>
                <template v-if="item.createTime"> · {{ formatTime(item.createTime) }}</template>
              </span>
            </span>
          </button>
        </section>

        <CommentSection
          :post-id="idStr(post.postId)"
          :author-id="idStr(post.userId)"
          :comment-count="Number(post.commentCount) || 0"
          @need-login="showLogin = true"
          @count-change="onCountChange"
        />
      </article>
    </main>

    <LoginDialog v-if="showLogin" @close="showLogin = false" />
    <PostDialog
      v-if="showEdit && post"
      :post-id="idStr(post.postId)"
      :initial="editInitial"
      @close="showEdit = false"
      @need-login="showLogin = true"
      @success="onEditSuccess"
    />
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f3f4f6;
  color: #111827;
}
.topbar {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  z-index: 3;
}
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}
.back img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.title {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.owner-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}
.owner-btn {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
}
.owner-btn.danger {
  color: #dc2626;
  border-color: #fecaca;
}
.owner-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.wrap {
  max-width: 760px;
  margin: 0 auto;
  padding: 16px;
}
.banner {
  text-align: center;
  color: #6b7280;
  padding: 12px;
}
.banner.error {
  color: #dc2626;
}
.sheet {
  background: #fff;
  border-radius: 12px;
  padding: 18px 18px 8px;
}
.author {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  cursor: pointer;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #e8eaed;
  color: #6b7280;
  display: grid;
  place-items: center;
  overflow: hidden;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.name {
  font-weight: 700;
}
.sub {
  margin-top: 2px;
  color: #9ca3af;
  font-size: 12px;
  display: flex;
  gap: 8px;
}
h1 {
  margin: 14px 0 8px;
  font-size: 20px;
}
.content {
  margin: 0 0 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.login-hint {
  color: #9ca3af;
  font-size: 14px;
}
.photo {
  display: block;
  max-width: 100%;
  border-radius: 8px;
  margin-bottom: 10px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 8px 0 12px;
  border-bottom: 1px solid #f3f4f6;
  color: #9aa0a6;
  font-size: 14px;
}
.actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  font-size: 14px;
}
.actions img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.comment-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.actions button.on {
  color: #3b6cff;
}
.comment-count {
  margin-left: auto;
}
.banner.pending {
  color: #b45309;
}
.banner.rejected {
  color: #b91c1c;
}
.banner-retry {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: inherit;
  padding: 0;
  text-decoration: underline;
}
.similar {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}
.similar h2 {
  font-size: 16px;
  margin: 0 0 10px;
}
.similar-item {
  display: flex;
  width: 100%;
  gap: 10px;
  padding: 10px;
  margin-bottom: 8px;
  border: none;
  border-radius: 10px;
  background: #f9fafb;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.similar-item:hover {
  background: #f3f4f6;
}
.similar-cover {
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  object-fit: cover;
  border-radius: 8px;
}
.similar-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.similar-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.similar-preview {
  color: #6b7280;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.similar-meta {
  color: #9ca3af;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
