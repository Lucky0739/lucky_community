<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { request, requestPage } from '@/api/http'
import { idStr } from '@/utils/id'
import LoginDialog from '@/components/LoginDialog.vue'
import BarManageDialog from '@/components/BarManageDialog.vue'
import iconLike from '@/icons/dianzan.png'
import iconUnlike from '@/icons/quxiaodianzan.png'
import iconFavorite from '@/icons/shoucang.png'
import iconUnfavorite from '@/icons/quxiaoshoucang.png'
import iconComment from '@/icons/pinglunxiao.png'
import iconBack from '@/icons/fanhui.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const bar = ref(null)
const posts = ref([])
const nextCursor = ref('')
const nextScore = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const showLogin = ref(false)
const showBarEdit = ref(false)
const showFans = ref(false)
const fanUsers = ref([])
const fanLoading = ref(false)
const fanError = ref('')
const fanPageNum = ref(1)
const fanHasMore = ref(false)

const isOwner = computed(() => auth.loggedIn && idStr(auth.userId) === idStr(bar.value?.ownerUserId))

function formatCount(n) {
  const num = Number(n) || 0
  if (num >= 10000) {
    return `${(num / 10000).toFixed(1).replace(/\.0$/, '')}W`
  }
  return String(num)
}

function barAvatar(item) {
  if (item?.avatar) return item.avatar
  const name = item?.name || item?.barName || '吧'
  return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`
}

function authorAvatar(post) {
  if (post?.icon) return post.icon
  const name = post?.nickName || '用户'
  return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function openPost(post, focusComments = false) {
  const postId = idStr(post?.postId)
  if (!postId || postId === '0') return
  router.push({
    name: 'post',
    params: { postId },
    query: focusComments ? { comments: '1' } : {},
    state: {
      summary: {
        postId,
        userId: idStr(post.userId),
        nickName: post.nickName || '',
        icon: post.icon || '',
        barName: bar.value?.name || post.barName || '',
        title: post.title || '',
        content: post.contentPreview || '',
        images: post.coverImage ? [post.coverImage] : [],
        likeCount: post.likeCount ?? 0,
        commentCount: post.commentCount ?? 0,
        favoriteCount: post.favoriteCount ?? 0,
        liked: post.liked === true,
        favorited: post.favorited === true,
        createTime: post.createTime || '',
      },
    },
  })
}

/** GET /post/feed：贴吧内按热度翻页，lastScore 与 lastId 一起作为游标 */
function feedUrl(barId, lastId, lastScore) {
  const params = new URLSearchParams({
    barId: idStr(barId),
    pageSize: '20',
  })
  if (lastId) {
    params.set('lastId', idStr(lastId))
    params.set('lastScore', lastScore == null || lastScore === '' ? '0' : String(lastScore))
  }
  return `/post/feed?${params.toString()}`
}

function applyFeed(feed, append) {
  const records = feed?.records || []
  posts.value = append ? posts.value.concat(records) : records
  const hasMore = records.length && feed?.nextCursor
  nextCursor.value = hasMore ? idStr(feed.nextCursor) : ''
  nextScore.value = hasMore && feed?.nextScore != null ? String(feed.nextScore) : ''
}

async function load() {
  const barId = idStr(route.params.barId)
  if (!barId) return
  loading.value = true
  error.value = ''
  bar.value = null
  posts.value = []
  nextCursor.value = ''
  nextScore.value = ''
  try {
    const token = auth.loggedIn ? auth.accessToken : undefined
    const [detail, feed] = await Promise.all([
      request(`/bar/${encodeURIComponent(barId)}`, { token }),
      request(feedUrl(barId), { token }),
    ])
    bar.value = detail
    applyFeed(feed, false)
  } catch (e) {
    error.value = e.message || '贴吧加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  const barId = idStr(route.params.barId)
  if (!barId || !nextCursor.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const token = auth.loggedIn ? auth.accessToken : undefined
    const feed = await request(feedUrl(barId, nextCursor.value, nextScore.value), { token })
    applyFeed(feed, true)
  } catch (e) {
    error.value = e.message || '加载更多失败'
  } finally {
    loadingMore.value = false
  }
}

async function toggleLike(post) {
  const postId = idStr(post?.postId)
  if (!postId || postId === '0') return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const liked = post.liked === true
  try {
    await request(`/thumb/${postId}`, {
      method: liked ? 'DELETE' : 'POST',
      token: auth.accessToken,
    })
    const delta = liked ? -1 : 1
    posts.value = posts.value.map((p) => {
      if (idStr(p.postId) !== postId) return p
      return {
        ...p,
        liked: !liked,
        likeCount: Math.max(0, (Number(p.likeCount) || 0) + delta),
      }
    })
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

async function toggleFavorite(post) {
  const postId = idStr(post?.postId)
  if (!postId || postId === '0') return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const favorited = post.favorited === true
  try {
    await request(`/favorite/${postId}`, {
      method: favorited ? 'DELETE' : 'POST',
      token: auth.accessToken,
    })
    const delta = favorited ? -1 : 1
    posts.value = posts.value.map((p) => {
      if (idStr(p.postId) !== postId) return p
      return {
        ...p,
        favorited: !favorited,
        favoriteCount: Math.max(0, (Number(p.favoriteCount) || 0) + delta),
      }
    })
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function onBarSaved(next) {
  bar.value = { ...bar.value, ...next }
  showBarEdit.value = false
}

async function loadFans(append) {
  const barId = idStr(bar.value?.barId)
  if (!barId || !auth.loggedIn) {
    showLogin.value = true
    return
  }
  fanLoading.value = true
  fanError.value = ''
  const pageNum = append ? fanPageNum.value + 1 : 1
  try {
    const page = await requestPage(
      `/follow/bar/${barId}/fans/page?pageNum=${pageNum}&pageSize=20`,
      { token: auth.accessToken },
    )
    const records = page.records || []
    fanUsers.value = append ? fanUsers.value.concat(records) : records
    fanPageNum.value = pageNum
    fanHasMore.value = fanUsers.value.length < (Number(page.total) || 0)
    showFans.value = true
  } catch (e) {
    fanError.value = e.message || '粉丝列表加载失败'
  } finally {
    fanLoading.value = false
  }
}

function openFanUser(userId) {
  const id = idStr(userId)
  if (!id) return
  showFans.value = false
  router.push({ name: 'user', params: { userId: id } })
}

async function toggleFollow() {
  if (!bar.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const barId = idStr(bar.value.barId)
  const followed = bar.value.followed === true
  try {
    await request(`/follow/${barId}`, {
      method: followed ? 'DELETE' : 'POST',
      token: auth.accessToken,
    })
    const delta = followed ? -1 : 1
    bar.value = {
      ...bar.value,
      followed: !followed,
      followCount: Math.max(0, (Number(bar.value.followCount) || 0) + delta),
    }
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

onMounted(load)
watch(() => route.params.barId, load)
watch(() => auth.loggedIn, load)
</script>

<template>
  <div class="page">
    <header class="topbar">
      <button type="button" class="back" aria-label="返回首页" @click="router.push({ name: 'home' })">
        <img :src="iconBack" alt="" />
        首页
      </button>
      <span class="title">{{ bar?.name || '贴吧' }}</span>
    </header>

    <main class="wrap">
      <p v-if="error" class="banner error">{{ error }}</p>
      <p v-if="loading" class="banner">加载中…</p>

      <section v-else-if="bar" class="hero">
        <img class="avatar" :src="barAvatar(bar)" alt="" />
        <div class="meta">
          <h1>{{ bar.name }}</h1>
          <p v-if="bar.description" class="desc">{{ bar.description }}</p>
          <p class="stats">
            <button type="button" class="stat-btn" @click="loadFans(false)">粉丝 {{ formatCount(bar.followCount) }}</button>
            <span>帖子 {{ formatCount(bar.postCount) }}</span>
          </p>
        </div>
        <div class="hero-actions">
          <button v-if="isOwner" type="button" class="manage" @click="showBarEdit = true">管理吧</button>
          <button type="button" class="follow" :class="{ on: bar.followed }" @click="toggleFollow">
            {{ bar.followed ? '已关注' : '关注' }}
          </button>
        </div>
      </section>

      <p v-if="bar && !loading && !posts.length" class="banner">这个吧还没有可见帖子</p>

      <article v-for="post in posts" :key="post.postId" class="post" @click="openPost(post)">
        <div class="author">
          <img class="author-icon" :src="authorAvatar(post)" alt="" />
          <span>{{ post.nickName || '用户' }}</span>
          <time v-if="post.createTime">{{ formatTime(post.createTime) }}</time>
        </div>
        <h2>{{ post.title }}</h2>
        <p v-if="post.contentPreview" class="preview">{{ post.contentPreview }}</p>
        <img v-if="post.coverImage" class="cover" :src="post.coverImage" alt="" />
        <footer>
          <button type="button" class="count-btn" @click.stop="openPost(post, true)">
            <img class="act" :src="iconComment" alt="" /> {{ post.commentCount ?? 0 }}
          </button>
          <button type="button" class="count-btn" @click.stop="toggleLike(post)">
            <img class="act" :src="post.liked ? iconUnlike : iconLike" alt="" /> {{ post.likeCount ?? 0 }}
          </button>
          <button type="button" class="count-btn" @click.stop="toggleFavorite(post)">
            <img class="act" :src="post.favorited ? iconUnfavorite : iconFavorite" alt="" /> {{ post.favoriteCount ?? 0 }}
          </button>
        </footer>
      </article>

      <button v-if="nextCursor" type="button" class="more" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? '加载中…' : '加载更多' }}
      </button>
    </main>

    <LoginDialog v-if="showLogin" @close="showLogin = false" />
    <BarManageDialog v-if="showBarEdit && bar" :bar="bar" @close="showBarEdit = false" @saved="onBarSaved" />

    <div v-if="showFans" class="mask" @click.self="showFans = false">
      <section class="fan-sheet">
        <header>
          <h2>吧粉丝</h2>
          <button type="button" class="sheet-close" aria-label="关闭" @click="showFans = false">×</button>
        </header>
        <p v-if="fanError" class="fan-empty">{{ fanError }}</p>
        <p v-else-if="fanLoading && !fanUsers.length" class="fan-empty">加载中…</p>
        <p v-else-if="!fanUsers.length" class="fan-empty">还没有粉丝</p>
        <button
          v-for="user in fanUsers"
          :key="user.userId"
          type="button"
          class="fan-row"
          @click="openFanUser(user.userId)"
        >
          <img v-if="user.icon" :src="user.icon" alt="" />
          <span v-else class="fan-fallback">{{ (user.nickName || '用').slice(0, 1) }}</span>
          <span>{{ user.nickName || '用户' }}</span>
        </button>
        <button v-if="fanHasMore" type="button" class="fan-more" :disabled="fanLoading" @click="loadFans(true)">
          {{ fanLoading ? '加载中…' : '加载更多' }}
        </button>
      </section>
    </div>
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
}
.wrap {
  max-width: 760px;
  margin: 0 auto;
  padding: 16px;
}
.banner {
  text-align: center;
  color: #6b7280;
  padding: 16px;
}
.banner.error {
  color: #dc2626;
}
.hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}
.avatar {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  object-fit: cover;
  background: #eee;
}
.meta {
  flex: 1;
  min-width: 0;
}
.meta h1 {
  margin: 0 0 6px;
  font-size: 20px;
}
.desc {
  margin: 0 0 8px;
  color: #4b5563;
  font-size: 14px;
}
.stats {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
  display: flex;
  gap: 12px;
}
.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.manage {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  border-radius: 18px;
  padding: 6px 16px;
  cursor: pointer;
}
.follow {
  border: none;
  background: #e8f2ff;
  color: #2b6cff;
  border-radius: 18px;
  padding: 6px 16px;
  cursor: pointer;
}
.follow.on {
  background: #f0f0f0;
  color: #888;
}
.stat-btn {
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  padding: 0;
  cursor: pointer;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.45);
  display: grid;
  place-items: center;
  z-index: 25;
  padding: 24px;
}
.fan-sheet {
  width: min(420px, 100%);
  max-height: min(560px, calc(100vh - 48px));
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
}
.fan-sheet header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sheet-close {
  border: none;
  background: transparent;
  font-size: 24px;
  cursor: pointer;
}
.fan-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 10px 4px;
  cursor: pointer;
  text-align: left;
}
.fan-row img,
.fan-fallback {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  background: #e8eef8;
  color: #3b6cff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.fan-empty,
.fan-more {
  text-align: center;
  color: #9ca3af;
  padding: 16px 8px;
}
.fan-more {
  width: 100%;
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
}
.post {
  background: #f4f6f8;
  border-radius: 16px;
  padding: 16px 18px 12px;
  margin-bottom: 12px;
  cursor: pointer;
}
.author {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}
.author-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: #eee;
}
.author time {
  margin-left: auto;
  color: #9ca3af;
}
.post h2 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
}
.preview {
  margin: 0 0 12px;
  color: #222;
  font-size: 16px;
  line-height: 1.5;
}
.cover {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: cover;
  border-radius: 12px;
  background: #e8eaed;
}
.post footer {
  display: flex;
  gap: 48px;
  margin-top: 14px;
  padding: 2px 2px 4px;
  color: #9aa0a6;
  font-size: 14px;
}
.post footer span,
.post footer .count-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.count-btn {
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  padding: 0;
  cursor: pointer;
}
.act {
  width: 22px;
  height: 22px;
  object-fit: contain;
}
.more {
  display: block;
  margin: 8px auto 24px;
  border: none;
  background: #fff;
  border-radius: 18px;
  padding: 8px 20px;
  cursor: pointer;
  color: #2563eb;
}
</style>
