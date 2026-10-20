<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { request, requestPage, searchPosts } from '@/api/http'
import { idStr } from '@/utils/id'
import LoginDialog from '@/components/LoginDialog.vue'
import PostDialog from '@/components/PostDialog.vue'
import BarManageDialog from '@/components/BarManageDialog.vue'
import iconHome from '@/icons/shouye_3.png'
import iconFollow from '@/icons/guanzhu.png'
import iconMine from '@/icons/wodedangxuan.png'
import iconLike from '@/icons/dianzan.png'
import iconUnlike from '@/icons/quxiaodianzan.png'
import iconFavorite from '@/icons/shoucang.png'
import iconUnfavorite from '@/icons/quxiaoshoucang.png'
import iconComment from '@/icons/pinglunxiao.png'
import iconSearch from '@/icons/sousuo.png'

const auth = useAuthStore()
const router = useRouter()
const showLogin = ref(false)
const showPost = ref(false)
const showCreateBar = ref(false)
const keyword = ref('')
const searching = ref(false)
const searchLoading = ref(false)
const searchError = ref('')
const searchRecords = ref([])
const searchTotal = ref(0)
const searchPage = ref(1)

const loading = ref(false)
const error = ref('')
const cards = ref([])
const followBars = ref([])
const followedBarIds = ref(new Set())
const navActive = ref('home')

function formatCount(n) {
  const num = Number(n) || 0
  if (num >= 10000) {
    return `${(num / 10000).toFixed(1).replace(/\.0$/, '')}W`
  }
  return String(num)
}

function barAvatar(bar) {
  if (bar?.avatar) return bar.avatar
  const name = bar?.name || '吧'
  return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}`
}

const PLACEHOLDER_POST = {
  postId: '0',
  title: '暂无可见帖子',
  contentPreview: '该吧还没有通过审核的帖子，或内容正在审核中',
}

/** 方案一：/bar/page；吧字段与 latestPost 上的 bar* 字段互为补充 */
function cardsFromDiscoveryBars(bars) {
  return (bars || []).map((bar) => {
    const post = bar.latestPost ?? PLACEHOLDER_POST
    const mergedBar = {
      ...bar,
      followCount: bar.followCount ?? post.barFollowCount,
      postCount: bar.postCount ?? post.barPostCount,
      followed: bar.followed === true || post.barFollowed === true,
    }
    return {
      bar: mergedBar,
      post,
      followed: mergedBar.followed === true,
    }
  })
}

function cardsFromFeedCards(records) {
  return (records || []).map((c) => ({
    bar: {
      barId: c.barId,
      name: c.barName,
      avatar: c.barAvatar,
      followCount: c.barFollowCount,
      postCount: c.barPostCount,
    },
    post: {
      postId: c.postId,
      title: c.title,
      contentPreview: c.contentPreview,
      coverImage: c.coverImage,
      commentCount: c.commentCount,
      likeCount: c.likeCount,
      barName: c.barName,
    },
    followed: c.followed,
  }))
}

async function loadDiscovery() {
  loading.value = true
  error.value = ''
  cards.value = []
  try {
    const token = auth.loggedIn ? auth.accessToken : undefined
    const barPage = await requestPage('/bar/page?pageNum=1', { token })
    const bars = barPage.records || []
    followedBarIds.value = new Set(bars.filter((b) => b.followed).map((b) => b.barId))
    cards.value = cardsFromDiscoveryBars(bars)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFollowFeed() {
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  loading.value = true
  error.value = ''
  cards.value = []
  followBars.value = []
  const token = auth.accessToken
  try {
    try {
      await request('/follow/feed/pull', { method: 'POST', token })
    } catch {
      // 非致命
    }
    const [cardsPage, followPage] = await Promise.all([
      requestPage('/follow/feed/cards?pageNo=1&pageSize=20', { token }),
      requestPage('/follow/page?pageNum=1&pageSize=50', { token }),
    ])
    followBars.value = followPage.records || []
    followedBarIds.value = new Set(followBars.value.map((b) => b.barId))
    cards.value = cardsFromFeedCards(cardsPage.records)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function syncFollowedFromCards() {
  if (!auth.loggedIn) return
  requestPage('/follow/page?pageNum=1&pageSize=50', { token: auth.accessToken })
    .then(({ records }) => {
      followBars.value = records || []
      followedBarIds.value = new Set(followBars.value.map((b) => b.barId))
    })
    .catch(() => {})
}

function switchNav(tab) {
  if (tab === 'mine') {
    openProfile()
    return
  }
  if (tab === 'follow' && !auth.loggedIn) {
    showLogin.value = true
    return
  }
  navActive.value = tab
  error.value = ''
  if (tab === 'home') {
    loadDiscovery()
  } else if (tab === 'follow') {
    loadFollowFeed()
  }
}

async function toggleFollow(barId) {
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  const id = idStr(barId)
  const token = auth.accessToken
  const followed = followedBarIds.value.has(id)
  try {
    if (followed) {
      await request(`/follow/${id}`, { method: 'DELETE', token })
      followedBarIds.value.delete(id)
      followBars.value = followBars.value.filter((b) => b.barId !== id)
    } else {
      await request(`/follow/${id}`, { method: 'POST', token })
      followedBarIds.value.add(id)
    }
    followedBarIds.value = new Set(followedBarIds.value)
    const nowFollowed = !followed
    cards.value = cards.value.map((c) => {
      if (c.bar.barId !== id) return c
      const delta = nowFollowed ? 1 : -1
      return {
        ...c,
        followed: nowFollowed,
        bar: {
          ...c.bar,
          followed: nowFollowed,
          followCount: Math.max(0, (Number(c.bar.followCount) || 0) + delta),
        },
      }
    })
    if (navActive.value === 'follow' && followed) {
      cards.value = cards.value.filter(({ bar }) => bar.barId !== id)
    }
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
    cards.value = cards.value.map((c) => {
      if (idStr(c.post.postId) !== postId) return c
      return {
        ...c,
        post: {
          ...c.post,
          favorited: !favorited,
          favoriteCount: Math.max(0, (Number(c.post.favoriteCount) || 0) + delta),
        },
      }
    })
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function openProfile(userId) {
  const id = idStr(userId || auth.userId)
  if (!id) {
    showLogin.value = true
    return
  }
  router.push({ name: 'user', params: { userId: id } })
}

function openBar(barId) {
  const id = idStr(barId)
  if (!id || id === '0') return
  router.push({ name: 'bar', params: { barId: id } })
}

function highlightTitle(post) {
  const source = post?.highlightTitle || post?.title || ''
  return String(source)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/&lt;strong&gt;/g, '<strong>')
    .replace(/&lt;\/strong&gt;/g, '</strong>')
}

async function runSearch(page = 1) {
  const q = keyword.value.trim()
  if (!q) {
    searching.value = false
    searchRecords.value = []
    searchTotal.value = 0
    searchError.value = ''
    return
  }
  searching.value = true
  searchLoading.value = true
  searchError.value = ''
  try {
    const pageData = await searchPosts(q, page)
    searchRecords.value = pageData.records
    searchTotal.value = Number(pageData.total) || 0
    searchPage.value = pageData.pageNo || page
  } catch (e) {
    searchRecords.value = []
    searchTotal.value = 0
    searchError.value = e.message || '搜索失败'
  } finally {
    searchLoading.value = false
  }
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
        barName: post.barName || '',
        title: post.title || '',
        content: post.content || post.contentPreview || '',
        images: post.images?.length ? post.images : (post.coverImage ? [post.coverImage] : []),
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
    cards.value = cards.value.map((c) => {
      if (idStr(c.post.postId) !== postId) return c
      return {
        ...c,
        post: {
          ...c.post,
          liked: !liked,
          likeCount: Math.max(0, (Number(c.post.likeCount) || 0) + delta),
        },
      }
    })
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function dismissCard(barId, postId) {
  const bid = idStr(barId)
  const pid = idStr(postId)
  cards.value = cards.value.filter(
    (c) => !(idStr(c.bar.barId) === bid && idStr(c.post.postId) === pid),
  )
}

async function logout() {
  await auth.logout()
}

function reloadActiveTab() {
  if (navActive.value === 'follow') {
    loadFollowFeed()
  } else if (navActive.value === 'home') {
    loadDiscovery()
  }
}

onMounted(() => {
  loadDiscovery()
})

watch(
  () => auth.loggedIn,
  () => {
    reloadActiveTab()
  },
)

const emptyHint = computed(() => {
  if (navActive.value === 'follow') {
    return '还没有关注帖消息，去首页关注贴吧吧'
  }
  return '暂无贴吧，稍后再来看看'
})

const frequentBars = computed(() => followBars.value.slice(0, 8))

const popularBars = computed(() => {
  const seen = new Set()
  const list = []
  for (const { bar } of cards.value) {
    if (!bar?.barId || seen.has(bar.barId)) continue
    seen.add(bar.barId)
    list.push(bar)
    if (list.length >= 8) break
  }
  return list
})
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">
        <span class="logo">贴</span>
        <span class="name">幸运贴吧</span>
      </div>
      <form class="search" @submit.prevent="runSearch(1)">
        <img class="search-icon" :src="iconSearch" alt="" />
        <input v-model="keyword" type="search" placeholder="搜索帖子" />
      </form>
      <div class="actions">
        <button class="ghost" type="button" @click="showPost = true">＋ 发帖</button>
        <button v-if="auth.loggedIn" class="ghost" type="button" @click="showCreateBar = true">创建贴吧</button>
        <template v-if="auth.loggedIn">
          <button class="user" type="button" @click="openProfile()">
            <img v-if="auth.icon" :src="auth.icon" alt="" />
            <span v-else class="avatar">{{ (auth.nickName || '用').slice(0, 1) }}</span>
            {{ auth.nickName || auth.phone }}
          </button>
          <button v-if="auth.isAdmin" class="ghost" type="button" @click="router.push({ name: 'admin' })">管理</button>
          <button class="primary" type="button" @click="logout">退出</button>
        </template>
        <button v-else class="primary" type="button" @click="showLogin = true">登录</button>
      </div>
    </header>

    <div class="shell">
      <aside class="sidebar">
        <nav class="nav">
          <button type="button" class="nav-item" :class="{ on: navActive === 'home' }" @click="switchNav('home')">
            <img class="nav-icon" :src="iconHome" alt="" />
            首页
          </button>
          <button type="button" class="nav-item" :class="{ on: navActive === 'follow' }" @click="switchNav('follow')">
            <img class="nav-icon" :src="iconFollow" alt="" />
            关注
          </button>
          <button type="button" class="nav-item" :class="{ on: navActive === 'mine' }" @click="switchNav('mine')">
            <img class="nav-icon" :src="iconMine" alt="" />
            我的
          </button>
        </nav>

        <section v-if="frequentBars.length" class="side-block">
          <h4 class="side-title">我常逛的吧</h4>
          <ul class="side-bars">
            <li v-for="bar in frequentBars" :key="bar.barId">
              <button type="button" class="side-bar" @click="openBar(bar.barId)">
                <img class="side-bar-avatar" :src="barAvatar(bar)" alt="" />
                <span class="side-bar-name">{{ bar.name }}</span>
              </button>
            </li>
          </ul>
        </section>

        <section v-if="popularBars.length" class="side-block">
          <h4 class="side-title">大家都在逛的吧</h4>
          <ul class="side-bars">
            <li v-for="bar in popularBars" :key="bar.barId">
              <button type="button" class="side-bar" @click="openBar(bar.barId)">
                <img class="side-bar-avatar" :src="barAvatar(bar)" alt="" />
                <span class="side-bar-name">{{ bar.name }}</span>
              </button>
            </li>
          </ul>
        </section>
      </aside>

      <div class="feed-col">
      <main v-if="searching" class="feed">
        <p v-if="searchError" class="banner error">{{ searchError }}</p>
        <p v-else-if="searchLoading" class="banner">搜索中…</p>
        <p v-else-if="!searchRecords.length" class="banner">没有找到相关帖子</p>
        <article
          v-for="post in searchRecords"
          :key="post.postId"
          class="feed-card search-card"
          @click="openPost(post)"
        >
          <h2 class="feed-post-title" v-html="highlightTitle(post)"></h2>
          <img v-if="post.coverImage" class="feed-post-cover" :src="post.coverImage" alt="" />
          <footer class="feed-card-foot">
            <span>点赞 {{ post.likeCount ?? 0 }}</span>
            <span v-if="post.createTime">{{ String(post.createTime).replace('T', ' ').slice(0, 16) }}</span>
          </footer>
        </article>
        <div v-if="searchTotal > searchRecords.length || searchPage > 1" class="search-pager">
          <button type="button" :disabled="searchPage <= 1 || searchLoading" @click="runSearch(searchPage - 1)">上一页</button>
          <span>{{ searchPage }} · 共 {{ searchTotal }} 条</span>
          <button
            type="button"
            :disabled="searchPage * 10 >= searchTotal || searchLoading"
            @click="runSearch(searchPage + 1)"
          >
            下一页
          </button>
        </div>
      </main>
      <main v-else class="feed">
        <p v-if="error" class="banner error">{{ error }}</p>

        <section v-if="navActive === 'follow' && auth.loggedIn && followBars.length" class="follow-bars">
          <h3 class="section-title">我关注的吧</h3>
          <ul class="bar-list">
            <li v-for="bar in followBars" :key="bar.barId" class="bar-row" @click="openBar(bar.barId)">
              <img class="bar-avatar sm" :src="barAvatar(bar)" alt="" />
              <span class="bar-row-name">{{ bar.name }}</span>
              <span class="bar-row-meta">{{ formatCount(bar.followCount) }}关注</span>
            </li>
          </ul>
        </section>

        <p v-if="loading" class="banner">加载中…</p>
        <p v-else-if="navActive !== 'mine' && !cards.length" class="banner">{{ emptyHint }}</p>

        <article
          v-for="{ bar, post, followed } in cards"
          v-show="navActive === 'home' || navActive === 'follow'"
          :key="`${bar.barId}-${post.postId}`"
          class="feed-card"
        >
          <header class="feed-card-head" @click="openBar(bar.barId)">
            <img class="feed-bar-avatar" :src="barAvatar(bar)" alt="" />
            <div class="feed-bar-info">
              <div class="feed-bar-name">{{ bar.name || post.barName }}</div>
              <div class="feed-bar-stats">
                <span>关注 {{ formatCount(bar.followCount) }}</span>
                <span>帖子 {{ formatCount(bar.postCount) }}</span>
              </div>
            </div>
            <div class="bar-hover-card" @click.stop="openBar(bar.barId)">
              <img class="bar-hover-avatar" :src="barAvatar(bar)" alt="" />
              <div class="bar-hover-body">
                <div class="bar-hover-name">{{ bar.name || post.barName }}</div>
                <p class="bar-hover-desc">{{ bar.description || '这个吧还没有简介' }}</p>
                <p v-if="bar.ownerNickName" class="bar-hover-owner">吧主 {{ bar.ownerNickName }}</p>
                <p class="bar-hover-stats">
                  <span>关注 {{ formatCount(bar.followCount) }}</span>
                  <span>帖子 {{ formatCount(bar.postCount) }}</span>
                  <span>{{ followed || followedBarIds.has(bar.barId) ? '已关注' : '未关注' }}</span>
                </p>
              </div>
            </div>
            <button
              type="button"
              class="feed-follow-btn"
              :class="{ on: followed || followedBarIds.has(bar.barId) }"
              @click.stop="toggleFollow(bar.barId)"
            >
              {{ followed || followedBarIds.has(bar.barId) ? '已关注' : '关注' }}
            </button>
          </header>

          <div class="feed-card-body" @click="openPost(post)">
            <h2 class="feed-post-title">{{ post.title }}</h2>
            <p v-if="post.contentPreview" class="feed-post-text">{{ post.contentPreview }}</p>
            <img v-if="post.coverImage" class="feed-post-cover" :src="post.coverImage" alt="" />
          </div>

          <footer class="feed-card-foot">
            <div class="feed-foot-actions">
              <button
                type="button"
                class="feed-foot-item feed-foot-btn"
                :class="{ on: post.favorited }"
                @click="toggleFavorite(post)"
              >
                <img class="feed-foot-icon" :src="post.favorited ? iconUnfavorite : iconFavorite" alt="" />
                {{ post.favoriteCount ?? 0 }}
              </button>
              <button type="button" class="feed-foot-item feed-foot-btn" @click.stop="openPost(post, true)">
                <img class="feed-foot-icon" :src="iconComment" alt="" />
                {{ post.commentCount ?? 0 }}
              </button>
              <button
                type="button"
                class="feed-foot-item feed-foot-btn"
                :class="{ liked: post.liked }"
                @click="toggleLike(post)"
              >
                <img class="feed-foot-icon" :src="post.liked ? iconUnlike : iconLike" alt="" />
                {{ post.likeCount ?? 0 }}
              </button>
            </div>
            <button
              type="button"
              class="feed-foot-close"
              aria-label="关闭"
              @click="dismissCard(bar.barId, post.postId)"
            >
              ×
            </button>
          </footer>
        </article>
      </main>
      </div>
    </div>

    <LoginDialog v-if="showLogin" @close="showLogin = false" />
    <PostDialog
      v-if="showPost"
      @close="showPost = false"
      @need-login="showLogin = true"
      @success="reloadActiveTab"
    />
    <BarManageDialog
      v-if="showCreateBar"
      @close="showCreateBar = false"
      @saved="(bar) => { showCreateBar = false; openBar(bar?.barId) }"
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
  height: 64px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 28px;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  z-index: 5;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 140px;
  font-weight: 700;
}
.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #3b6cff;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 16px;
}
.search {
  flex: 1;
  max-width: 520px;
  margin: 0 auto;
  height: 40px;
  border-radius: 20px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  padding: 0 14px;
  gap: 8px;
  color: #9ca3af;
}
.search-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
  flex-shrink: 0;
}
.search input {
  border: none;
  background: transparent;
  width: 100%;
  font-size: 14px;
  color: #6b7280;
}
.search input:focus {
  outline: none;
}
.search-card {
  cursor: pointer;
}
.search-pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin: 8px 0 16px;
  color: #9ec3e4;
}
.search-pager button {
  border: 1px solid rgba(120, 200, 255, 0.4);
  background: rgba(40, 120, 200, 0.2);
  color: #e8f4ff;
  border-radius: 14px;
  padding: 6px 12px;
  cursor: pointer;
}
.search-pager button:disabled {
  opacity: 0.4;
  cursor: default;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.user img,
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar {
  background: #dbeafe;
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-size: 13px;
}
button {
  cursor: pointer;
  font-size: 14px;
}
.ghost {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #fff;
  color: #3b6cff;
  border: 1px solid #3b6cff;
}
.primary {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  background: #3b6cff;
  color: #fff;
  border: none;
}
.shell {
  display: flex;
  align-items: flex-start;
  width: 100%;
  min-height: calc(100vh - 64px);
}
.sidebar {
  position: sticky;
  top: 64px;
  flex: 0 0 220px;
  width: 220px;
  height: calc(100vh - 64px);
  overflow-y: auto;
  background: #fff;
  padding: 8px 10px 24px;
  box-sizing: border-box;
}
.feed-col {
  flex: 1;
  min-width: 0;
  padding: 12px 16px 32px 10px;
  box-sizing: border-box;
}
.feed {
  width: 100%;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 10px;
  padding: 10px 12px;
  color: #1f2937;
  text-align: left;
  font-size: 15px;
}
.nav-item.on {
  background: #f2f3f5;
  color: #111827;
  font-weight: 700;
}
.nav-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
  flex-shrink: 0;
}
.nav-item:not(.on) .nav-icon {
  opacity: 0.72;
}
.nav-item.on .nav-icon {
  opacity: 1;
}
.side-block {
  margin-top: 18px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
.side-title {
  margin: 8px 12px 6px;
  font-size: 13px;
  font-weight: 500;
  color: #9ca3af;
}
.side-bars {
  list-style: none;
  margin: 0;
  padding: 0;
}
.side-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.side-bar:hover {
  background: #f3f4f6;
}
.side-bar-avatar {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  object-fit: cover;
  background: #eee;
  flex-shrink: 0;
}
.side-bar-name {
  font-size: 14px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.banner {
  text-align: center;
  color: #6b7280;
  padding: 24px;
  font-size: 14px;
}
.banner.error {
  color: #dc2626;
}
/* 贴吧信息流卡片（参考贴子列表样式） */
.feed-card {
  background: #f4f6f8;
  border-radius: 16px;
  margin-bottom: 12px;
  padding: 16px 18px 8px;
  box-shadow: none;
  border: none;
}
.feed-card-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  border-radius: 10px;
  cursor: pointer;
}
.bar-hover-card {
  display: none;
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 6;
  width: min(360px, 100%);
  padding: 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  gap: 12px;
  cursor: pointer;
}
.feed-card-head:hover .bar-hover-card {
  display: flex;
}
.bar-hover-avatar {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  background: #eee;
  flex-shrink: 0;
}
.bar-hover-body {
  min-width: 0;
}
.bar-hover-name {
  font-size: 16px;
  font-weight: 700;
}
.bar-hover-desc,
.bar-hover-owner,
.bar-hover-stats {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.bar-hover-stats {
  display: flex;
  gap: 12px;
}
.feed-bar-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  background: #e8eaed;
  flex-shrink: 0;
  cursor: pointer;
}
.feed-bar-info {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
  cursor: pointer;
}
.feed-bar-name {
  font-size: 16px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-bar-stats {
  margin-top: 3px;
  font-size: 12px;
  color: #b0b4bd;
  display: flex;
  gap: 10px;
}
.feed-follow-btn {
  flex-shrink: 0;
  border: none;
  background: #e7f1ff;
  color: #5b92f5;
  border-radius: 16px;
  padding: 6px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.feed-follow-btn.on {
  background: #eceef2;
  color: #9aa0a6;
}
.feed-card-body {
  padding-bottom: 4px;
  cursor: pointer;
}
.feed-post-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 6px;
  line-height: 1.4;
}
.feed-post-text {
  font-size: 16px;
  color: #222;
  margin: 0 0 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.feed-post-cover {
  display: block;
  width: 100%;
  max-height: 420px;
  height: auto;
  object-fit: cover;
  border-radius: 12px;
  background: #e8eaed;
}
.feed-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding: 4px 2px 6px;
  color: #9aa0a6;
  font-size: 14px;
}
.feed-foot-actions {
  display: flex;
  align-items: center;
  gap: 48px;
}
.feed-foot-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.feed-foot-btn {
  border: none;
  background: transparent;
  color: inherit;
  padding: 0;
  cursor: pointer;
}
.feed-foot-btn.on {
  color: #f59e0b;
}
.feed-foot-btn.liked {
  color: #3b6cff;
}
.feed-foot-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}
.feed-foot-close {
  border: none;
  background: transparent;
  color: #c5c8ce;
  font-size: 20px;
  line-height: 1;
  padding: 4px 2px;
  cursor: pointer;
}
.feed-foot-close:hover {
  color: #999;
}
.follow-btn.sm {
  padding: 2px 10px;
  font-size: 12px;
  flex-shrink: 0;
}
.section-title {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 10px;
  font-weight: 600;
}
.follow-bars {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.bar-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  border-radius: 8px;
}
.bar-row:hover {
  background: #f3f4f6;
}
.bar-row:last-child {
  border-bottom: none;
}
.bar-avatar.sm {
  width: 32px;
  height: 32px;
}
.bar-row-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}
.bar-row-meta {
  font-size: 12px;
  color: #9ca3af;
}
.mine-panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.mine-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.mine-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-size: 22px;
  overflow: hidden;
}
.mine-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mine-name {
  font-size: 18px;
  font-weight: 600;
}
.mine-sub {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}
@media (max-width: 768px) {
  .shell {
    flex-direction: column;
  }
  .sidebar {
    flex: none;
    width: 100%;
    min-height: auto;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
    padding: 8px 12px;
  }
  .nav {
    flex-direction: row;
  }
  .feed-wrap {
    padding: 12px 16px 24px;
  }
  .search {
    display: none;
  }
}
</style>
