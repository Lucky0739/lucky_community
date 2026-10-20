<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { request, uploadFile } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'
import iconLike from '@/icons/dianzan.png'
import iconUnlike from '@/icons/quxiaodianzan.png'
import iconComment from '@/icons/pinglunxiao.png'

const props = defineProps({
  postId: { type: String, required: true },
  authorId: { type: String, default: '' },
  commentCount: { type: Number, default: 0 },
})

const emit = defineEmits(['need-login', 'count-change'])

const auth = useAuthStore()
const router = useRouter()
const EMOJIS = ['😀', '😂', '🥰', '😭', '👍', '🎉', '🔥', '❤️']
const MAX_LEN = 500

const comments = ref([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const sort = ref('hot')
const onlyAuthor = ref(false)
const draft = ref('')
const imageUrl = ref('')
const replyTo = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const showEmoji = ref(false)
const menuId = ref('')
const inputRef = ref(null)
const fileRef = ref(null)
const shownTotal = ref(0)

const hasMore = computed(() => pageNo.value * pageSize.value < Number(total.value || 0))

const visibleComments = computed(() => {
  let list = comments.value
  if (onlyAuthor.value && props.authorId) {
    list = list.filter((item) => idStr(item.userId) === idStr(props.authorId))
  }
  const copy = [...list]
  if (sort.value === 'hot') {
    copy.sort((a, b) => (Number(b.likeCount) || 0) - (Number(a.likeCount) || 0) || timeValue(b) - timeValue(a))
  } else if (sort.value === 'desc') {
    copy.sort((a, b) => timeValue(b) - timeValue(a))
  } else {
    copy.sort((a, b) => Number(b.top) - Number(a.top) || timeValue(a) - timeValue(b))
  }
  return copy
})

function timeValue(item) {
  const raw = item?.createTime
  if (!raw) return 0
  const parsed = new Date(String(raw).replace('T', ' ').replace(/-/g, '/')).getTime()
  return Number.isFinite(parsed) ? parsed : 0
}

function formatAgo(value) {
  const time = timeValue({ createTime: value })
  if (!time) return ''
  const diff = Date.now() - time
  const minute = Math.floor(diff / 60000)
  if (minute < 1) return '刚刚'
  if (minute < 60) return `${minute}分钟前`
  const hour = Math.floor(minute / 60)
  if (hour < 24) return `${hour}小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day}天前`
  return String(value).replace('T', ' ').slice(0, 16)
}

function initial(name) {
  return (name || '用').slice(0, 1)
}

function openUser(userId) {
  const id = idStr(userId)
  if (!id || id === '0') return
  router.push({ name: 'user', params: { userId: id } })
}

function replyTargetName(reply, root) {
  if (reply?.replyNickName) return reply.replyNickName
  const replyUserId = idStr(reply?.replyUserId)
  if (!replyUserId || replyUserId === '0') return ''
  if (replyUserId === idStr(root?.userId)) return root?.nickName || ''
  const pool = [root, root?.firstReplyComment, ...(root?.replies || [])]
  const hit = pool.find((item) => item && idStr(item.userId) === replyUserId && item.nickName)
  return hit?.nickName || ''
}

function shownReplies(root) {
  if (root.replies) return root.replies
  return root.firstReplyComment ? [root.firstReplyComment] : []
}

function hiddenReplyCount(root) {
  const count = Number(root.replyCount) || 0
  return Math.max(0, count - shownReplies(root).length)
}

function closePopovers() {
  showEmoji.value = false
  menuId.value = ''
}

function requireLogin() {
  if (auth.loggedIn) return true
  emit('need-login')
  return false
}

async function fetchPage(page) {
  return request('/comment/page', {
    method: 'POST',
    body: { postId: idStr(props.postId), pageNo: page },
    token: auth.loggedIn ? auth.accessToken : undefined,
  })
}

function applyPage(page, append) {
  const records = page?.records || []
  pageSize.value = Number(page?.pageSize) || records.length || 10
  total.value = Number(page?.total) || 0
  if ((Number(props.commentCount) || 0) > shownTotal.value) {
    shownTotal.value = Number(props.commentCount) || 0
  }
  if (total.value > shownTotal.value) {
    shownTotal.value = total.value
  }
  comments.value = append ? comments.value.concat(records) : records
}

async function load() {
  if (!props.postId) return
  loading.value = true
  error.value = ''
  pageNo.value = 1
  comments.value = []
  try {
    applyPage(await fetchPage(1), false)
  } catch (e) {
    error.value = e.message || '评论加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  error.value = ''
  try {
    const next = pageNo.value + 1
    applyPage(await fetchPage(next), true)
    pageNo.value = next
  } catch (e) {
    error.value = e.message || '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

function patchComment(commentId, updater) {
  const id = idStr(commentId)
  const visit = (item) => {
    if (!item) return item
    const next = idStr(item.commentId) === id ? updater(item) : item
    return {
      ...next,
      firstReplyComment: visit(next.firstReplyComment),
      replies: next.replies ? next.replies.map(visit) : next.replies,
    }
  }
  comments.value = comments.value.map(visit)
}

function findRootId(comment) {
  if (Number(comment?.level) === 2) return idStr(comment.parentId)
  return idStr(comment?.commentId)
}

async function toggleLike(comment) {
  if (!requireLogin()) return
  const liked = comment.liked === true
  const before = { liked: comment.liked === true, likeCount: Number(comment.likeCount) || 0 }
  patchComment(comment.commentId, (item) => ({
    ...item,
    liked: !liked,
    likeCount: Math.max(0, before.likeCount + (liked ? -1 : 1)),
  }))
  try {
    await request(liked ? '/comment/unlike' : '/comment/like', {
      method: 'POST',
      body: { commentId: idStr(comment.commentId) },
      token: auth.accessToken,
    })
  } catch (e) {
    patchComment(comment.commentId, (item) => ({ ...item, ...before }))
    error.value = e.message || '操作失败'
  }
}

function startReply(comment) {
  if (!requireLogin()) return
  replyTo.value = comment
  menuId.value = ''
  nextTick(() => inputRef.value?.focus())
}

function cancelReply() {
  replyTo.value = null
}

function insertAtCursor(text) {
  const el = inputRef.value
  if (!el) {
    draft.value = (draft.value + text).slice(0, MAX_LEN)
    return
  }
  const start = el.selectionStart ?? draft.value.length
  const end = el.selectionEnd ?? draft.value.length
  draft.value = (draft.value.slice(0, start) + text + draft.value.slice(end)).slice(0, MAX_LEN)
  nextTick(() => {
    const pos = Math.min(start + text.length, draft.value.length)
    el.focus()
    el.setSelectionRange(pos, pos)
  })
}

function pickImage() {
  if (!requireLogin()) return
  fileRef.value?.click()
}

async function onImage(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!requireLogin()) return
  uploading.value = true
  error.value = ''
  try {
    imageUrl.value = await uploadFile('/comment/image', file, auth.accessToken)
  } catch (e) {
    error.value = e.message || '图片上传失败'
  } finally {
    uploading.value = false
  }
}

function rememberLocal(item, rootId) {
  if (!rootId) {
    comments.value = comments.value.concat(item)
    total.value += 1
    return
  }
  comments.value = comments.value.map((root) => {
    if (idStr(root.commentId) !== rootId) return root
    const replies = shownReplies(root).concat(item)
    return {
      ...root,
      replyCount: (Number(root.replyCount) || 0) + 1,
      replies,
      firstReplyComment: root.firstReplyComment || item,
    }
  })
}

async function publish() {
  const content = draft.value.trim()
  if (!content && !imageUrl.value) return
  if (!requireLogin()) return
  submitting.value = true
  error.value = ''
  const replying = replyTo.value
  try {
    const commentId = await request('/comment/create', {
      method: 'POST',
      token: auth.accessToken,
      body: {
        postId: idStr(props.postId),
        content,
        imageUrl: imageUrl.value || undefined,
        replyCommentId: replying ? idStr(replying.commentId) : undefined,
      },
    })
    onlyAuthor.value = false
    rememberLocal({
      commentId: idStr(commentId),
      postId: idStr(props.postId),
      userId: auth.userId,
      nickName: auth.nickName || '我',
      icon: auth.icon,
      level: replying ? 2 : 1,
      parentId: replying ? findRootId(replying) : idStr(props.postId),
      replyCommentId: replying ? idStr(replying.commentId) : '0',
      replyUserId: replying ? idStr(replying.userId) : '0',
      replyNickName: replying?.nickName || '',
      content,
      imageUrl: imageUrl.value || null,
      replyCount: 0,
      likeCount: 0,
      liked: false,
      top: false,
      createTime: new Date().toISOString(),
      pending: true,
    }, replying ? findRootId(replying) : '')
    shownTotal.value += 1
    emit('count-change', 1)
    draft.value = ''
    imageUrl.value = ''
    replyTo.value = null
    showEmoji.value = false
  } catch (e) {
    error.value = e.message || '发布失败'
  } finally {
    submitting.value = false
  }
}

function owns(comment) {
  return auth.loggedIn && auth.userId && idStr(comment.userId) === idStr(auth.userId)
}

function removeLocal(comment) {
  const id = idStr(comment.commentId)
    if (Number(comment.level) === 2) {
    const rootId = idStr(comment.parentId)
    comments.value = comments.value.map((root) => {
      if (idStr(root.commentId) !== rootId) return root
      const replies = shownReplies(root).filter((item) => idStr(item.commentId) !== id)
      return {
        ...root,
        replyCount: Math.max(0, (Number(root.replyCount) || 0) - 1),
        replies,
        firstReplyComment: idStr(root.firstReplyComment?.commentId) === id ? replies[0] || null : root.firstReplyComment,
      }
    })
    shownTotal.value = Math.max(0, shownTotal.value - 1)
    emit('count-change', -1)
    return
  }
  const removed = comments.value.find((item) => idStr(item.commentId) === id)
  comments.value = comments.value.filter((item) => idStr(item.commentId) !== id)
  total.value = Math.max(0, total.value - 1)
  const nested = Number(removed?.replyCount) || 0
  shownTotal.value = Math.max(0, shownTotal.value - 1 - nested)
  emit('count-change', -(1 + nested))
}

async function removeComment(comment) {
  if (!requireLogin()) return
  menuId.value = ''
  const snapshot = comments.value
  const totalBefore = total.value
  const shownBefore = shownTotal.value
  removeLocal(comment)
  try {
    await request('/comment/delete', {
      method: 'POST',
      token: auth.accessToken,
      body: { commentId: idStr(comment.commentId) },
    })
  } catch (e) {
    comments.value = snapshot
    total.value = totalBefore
    shownTotal.value = shownBefore
    emit('count-change', Number(comment.level) === 2 ? 1 : 1 + (Number(comment.replyCount) || 0))
    error.value = e.message || '删除失败'
  }
}

async function expandReplies(root) {
  const nextPage = root.replies ? (root.replyPage || 1) + 1 : 1
  error.value = ''
  try {
    const page = await request('/comment/reply/page', {
      method: 'POST',
      token: auth.loggedIn ? auth.accessToken : undefined,
      body: {
        postId: idStr(props.postId),
        rootCommentId: idStr(root.commentId),
        pageNo: nextPage,
      },
    })
    const incoming = page?.records || []
    const merged = nextPage === 1 ? incoming : [...(root.replies || []), ...incoming]
    const seen = new Set()
    const replies = merged.filter((item) => {
      const id = idStr(item.commentId)
      if (seen.has(id)) return false
      seen.add(id)
      return true
    })
    patchComment(root.commentId, (item) => ({
      ...item,
      replies,
      replyPage: nextPage,
      replyCount: Number(page?.total ?? item.replyCount) || replies.length,
    }))
  } catch (e) {
    error.value = e.message || '回复加载失败'
  }
}

function onFocus() {
  if (!auth.loggedIn) {
    inputRef.value?.blur()
    emit('need-login')
  }
}

watch(() => props.postId, load)
watch(() => props.commentCount, (value) => {
  const num = Number(value) || 0
  if (num > shownTotal.value) shownTotal.value = num
})
watch(() => auth.loggedIn, (logged) => {
  if (logged) load()
})

onMounted(() => {
  shownTotal.value = Number(props.commentCount) || 0
  load()
  document.addEventListener('click', closePopovers)
})
onUnmounted(() => document.removeEventListener('click', closePopovers))
</script>

<template>
  <section id="post-comments" class="comments">
    <header class="bar">
      <div class="tabs">
        <button type="button" class="tab" :class="{ on: !onlyAuthor }" @click="onlyAuthor = false">
          全部回复 ({{ shownTotal }})
        </button>
        <button
          type="button"
          class="tab"
          :class="{ on: onlyAuthor }"
          :disabled="!authorId"
          @click="onlyAuthor = true"
        >
          只看楼主
        </button>
      </div>
      <div class="sorts">
        <button type="button" :class="{ on: sort === 'hot' }" @click="sort = 'hot'">热门</button>
        <button type="button" :class="{ on: sort === 'asc' }" @click="sort = 'asc'">正序</button>
        <button type="button" :class="{ on: sort === 'desc' }" @click="sort = 'desc'">倒序</button>
      </div>
    </header>

    <div class="composer">
      <button type="button" class="avatar" @click="auth.userId && openUser(auth.userId)">
        <img v-if="auth.icon" :src="auth.icon" alt="" />
        <span v-else>{{ initial(auth.nickName) }}</span>
      </button>
      <div class="box">
        <p v-if="replyTo" class="replying">
          回复 {{ replyTo.nickName || '用户' }}
          <button type="button" @click="cancelReply">取消</button>
        </p>
        <textarea
          ref="inputRef"
          v-model="draft"
          :maxlength="MAX_LEN"
          rows="2"
          placeholder="参与讨论"
          @focus="onFocus"
        />
        <img v-if="imageUrl" class="preview" :src="imageUrl" alt="" />
        <div class="tools">
          <div class="tool-left">
            <button type="button" class="icon" aria-label="表情" @click.stop="showEmoji = !showEmoji">☺</button>
            <button type="button" class="icon" aria-label="提到" @click="insertAtCursor('@')">@</button>
            <button type="button" class="icon" aria-label="话题" @click="insertAtCursor('#')">#</button>
            <button type="button" class="icon" aria-label="图片" :disabled="uploading" @click="pickImage">▣</button>
            <div v-if="showEmoji" class="emoji-pop" @click.stop>
              <button v-for="emoji in EMOJIS" :key="emoji" type="button" @click="insertAtCursor(emoji)">
                {{ emoji }}
              </button>
            </div>
            <input ref="fileRef" class="file" type="file" accept="image/*" @change="onImage" />
          </div>
          <button
            type="button"
            class="send"
            :disabled="submitting || uploading || (!draft.trim() && !imageUrl)"
            @click="publish"
          >
            {{ submitting ? '发布中' : '发布' }}
          </button>
        </div>
      </div>
    </div>

    <p v-if="error" class="hint error">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>
    <p v-else-if="!visibleComments.length" class="hint">还没有回复，来发第一条</p>

    <article v-for="comment in visibleComments" :key="comment.commentId" class="thread">
      <div class="item">
        <button type="button" class="avatar sm" @click="openUser(comment.userId)">
          <img v-if="comment.icon" :src="comment.icon" alt="" />
          <span v-else>{{ initial(comment.nickName) }}</span>
        </button>
        <div class="body">
          <button type="button" class="name" @click="openUser(comment.userId)">
            {{ comment.nickName || '用户' }}
            <em v-if="comment.top">置顶</em>
          </button>
          <p class="text">{{ comment.content }}</p>
          <img v-if="comment.imageUrl" class="pic" :src="comment.imageUrl" alt="" />
          <div class="meta">
            <span>{{ formatAgo(comment.createTime) }}</span>
            <div class="ops">
              <button type="button" class="op" :class="{ on: comment.liked }" @click="toggleLike(comment)">
                <img :src="comment.liked ? iconUnlike : iconLike" alt="" />
                {{ comment.likeCount || '赞' }}
              </button>
              <button type="button" class="op" @click="startReply(comment)">
                <img :src="iconComment" alt="" />
                回复
              </button>
              <span v-if="owns(comment)" class="more-wrap">
                <button type="button" class="op more" aria-label="更多" @click.stop="menuId = menuId === comment.commentId ? '' : comment.commentId">···</button>
                <button v-if="menuId === comment.commentId" type="button" class="menu" @click="removeComment(comment)">删除</button>
              </span>
            </div>
          </div>

          <div v-for="reply in shownReplies(comment)" :key="reply.commentId" class="item reply">
            <button type="button" class="avatar sm" @click="openUser(reply.userId)">
              <img v-if="reply.icon" :src="reply.icon" alt="" />
              <span v-else>{{ initial(reply.nickName) }}</span>
            </button>
            <div class="body">
              <button type="button" class="name" @click="openUser(reply.userId)">{{ reply.nickName || '用户' }}</button>
              <p class="text">
                <template v-if="replyTargetName(reply, comment)">
                  <span class="reply-label">回复</span>
                  <span class="reply-name">{{ replyTargetName(reply, comment) }}</span>
                  <span>：</span>
                </template>
                {{ reply.content }}
              </p>
              <img v-if="reply.imageUrl" class="pic" :src="reply.imageUrl" alt="" />
              <div class="meta">
                <span>{{ formatAgo(reply.createTime) }}</span>
                <div class="ops">
                  <button type="button" class="op" :class="{ on: reply.liked }" @click="toggleLike(reply)">
                    <img :src="reply.liked ? iconUnlike : iconLike" alt="" />
                    {{ reply.likeCount || '赞' }}
                  </button>
                  <button type="button" class="op" @click="startReply(reply)">
                    <img :src="iconComment" alt="" />
                    回复
                  </button>
                  <span v-if="owns(reply)" class="more-wrap">
                    <button type="button" class="op more" aria-label="更多" @click.stop="menuId = menuId === reply.commentId ? '' : reply.commentId">···</button>
                    <button v-if="menuId === reply.commentId" type="button" class="menu" @click="removeComment(reply)">删除</button>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <button v-if="hiddenReplyCount(comment) > 0" type="button" class="expand" @click="expandReplies(comment)">
            {{ comment.replies ? '展开更多回复' : `查看全部${comment.replyCount}条回复` }}
          </button>
        </div>
      </div>
    </article>

    <button v-if="hasMore" type="button" class="more-page" :disabled="loadingMore" @click="loadMore">
      {{ loadingMore ? '加载中…' : '查看更多回复' }}
    </button>
  </section>
</template>

<style scoped>
.comments {
  margin-top: 8px;
  padding-top: 8px;
}
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.tabs {
  display: flex;
  align-items: flex-end;
  gap: 28px;
}
.tab {
  border: none;
  background: transparent;
  padding: 0 0 8px;
  font-size: 16px;
  color: #9aa0a6;
  cursor: pointer;
  position: relative;
}
.tab.on {
  color: #1a1a1a;
  font-weight: 700;
}
.tab.on::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: #4c8dff;
}
.tab:disabled {
  cursor: default;
}
.sorts {
  display: flex;
  padding: 3px;
  border-radius: 16px;
  background: #f2f3f5;
}
.sorts button {
  border: none;
  background: transparent;
  color: #8b9098;
  border-radius: 13px;
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
}
.sorts button.on {
  background: #fff;
  color: #222;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
.composer {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.avatar {
  width: 40px;
  height: 40px;
  border: none;
  padding: 0;
  border-radius: 50%;
  background: #e8eaed;
  color: #8b9098;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex-shrink: 0;
  font-size: 16px;
  cursor: pointer;
}
.avatar.sm {
  width: 36px;
  height: 36px;
  font-size: 14px;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.box {
  flex: 1;
  border: 1px solid #8eb6ff;
  border-radius: 10px;
  padding: 10px 12px 8px;
  background: #fff;
}
.replying {
  margin: 0 0 6px;
  font-size: 13px;
  color: #6b7280;
}
.replying button {
  border: none;
  background: transparent;
  color: #4c8dff;
  cursor: pointer;
  margin-left: 8px;
}
.box textarea {
  width: 100%;
  border: none;
  resize: none;
  outline: none;
  font: inherit;
  font-size: 15px;
  line-height: 1.5;
  color: #222;
  background: transparent;
}
.preview,
.pic {
  display: block;
  width: 160px;
  max-width: 100%;
  border-radius: 8px;
  margin-top: 8px;
}
.tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.tool-left {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
}
.icon {
  border: none;
  background: transparent;
  color: #9aa0a6;
  font-size: 18px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}
.file {
  display: none;
}
.emoji-pop {
  position: absolute;
  left: 0;
  bottom: 28px;
  display: flex;
  gap: 4px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}
.emoji-pop button {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
}
.send {
  border: none;
  background: #6aa1ff;
  color: #fff;
  border-radius: 18px;
  padding: 6px 18px;
  cursor: pointer;
  font-size: 14px;
}
.send:disabled {
  opacity: 0.45;
  cursor: default;
}
.hint {
  text-align: center;
  color: #9aa0a6;
  font-size: 14px;
  padding: 18px 0;
}
.hint.error {
  color: #dc2626;
}
.thread + .thread {
  border-top: 1px solid #f3f4f6;
}
.item {
  display: flex;
  gap: 12px;
  padding: 14px 0 4px;
}
.item.reply {
  padding-top: 10px;
}
.body {
  flex: 1;
  min-width: 0;
}
.name {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 15px;
  font-weight: 700;
  color: #222;
  cursor: pointer;
}
.name em {
  margin-left: 6px;
  font-style: normal;
  font-size: 12px;
  font-weight: 500;
  color: #f59e0b;
}
.text {
  margin: 6px 0 8px;
  font-size: 15px;
  line-height: 1.65;
  color: #222;
  white-space: pre-wrap;
  word-break: break-word;
}
.reply-label {
  color: #8b9098;
  margin-right: 4px;
}
.reply-name {
  color: #3b6cff;
  font-weight: 600;
}
.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #b0b4ba;
  font-size: 13px;
}
.ops {
  display: flex;
  align-items: center;
  gap: 18px;
}
.op {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  font-size: 13px;
}
.op img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.op.on {
  color: #3b6cff;
}
.more-wrap {
  position: relative;
}
.more {
  letter-spacing: 1px;
  font-size: 16px;
}
.menu {
  position: absolute;
  right: 0;
  top: 22px;
  z-index: 2;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 8px;
  padding: 6px 14px;
  color: #dc2626;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}
.expand,
.more-page {
  border: none;
  background: transparent;
  color: #8b9098;
  cursor: pointer;
  font-size: 13px;
  padding: 4px 0 8px;
}
.more-page {
  display: block;
  margin: 8px auto 4px;
}
</style>
