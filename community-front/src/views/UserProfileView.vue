<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, requestPage } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'
import LoginDialog from '@/components/LoginDialog.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
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

const profile = ref(null)
const loading = ref(false)
const error = ref('')
const tab = ref('posts')
const posts = ref([])
const nextCursor = ref('')
const listLoading = ref(false)
const listError = ref('')
const showEdit = ref(false)
const showLogin = ref(false)
const editPostId = ref('')
const editPostInitial = ref(null)
const deletingPostId = ref('')
const relation = ref('')
const followBars = ref([])
const followTotal = ref(null)
const ownedBars = ref([])
const ownedFanTotal = ref(null)
const fanUsers = ref([])
const fanBarName = ref('')
const fanBarId = ref('')
const fanPageNum = ref(1)
const fanHasMore = ref(false)
const relationLoading = ref(false)
const relationError = ref('')
const replies = ref([])
const replyCursor = ref('')
const replyHasMore = ref(false)
const interactionPageNum = ref(1)
const interactionHasMore = ref(false)
const interactionTotal = ref(0)

const isSelf = computed(() => auth.loggedIn && idStr(auth.userId) === idStr(route.params.userId))
const shownFollow = computed(() => (followTotal.value == null ? Number(profile.value?.followee) || 0 : followTotal.value))
const shownFans = computed(() => {
  if (ownedFanTotal.value != null) return ownedFanTotal.value
  return Number(profile.value?.fans) || 0
})

const allTabs = [
  { id: 'posts', label: '贴子' },
  { id: 'replies', label: '回贴' },
  { id: 'likes', label: '点赞', selfOnly: true },
  { id: 'favorites', label: '收藏', selfOnly: true },
]

const visibleTabs = computed(() =>
  allTabs.filter((item) => !item.selfOnly || isSelf.value),
)

const emptyTabHint = computed(() => {
  if (tab.value === 'posts') return '还没有发布帖子'
  if (tab.value === 'likes') return '还没有点赞过的帖子'
  if (tab.value === 'favorites') return '还没有收藏过的帖子'
  return '什么都没有，这里一片荒芜...'
})

function formatCount(n) {
  const num = Number(n) || 0
  if (num >= 10000) return `${(num / 10000).toFixed(1).replace(/\.0$/, '')}W`
  return String(num)
}

function barAge(value) {
  if (!value) return ''
  const start = new Date(String(value).replace('T', ' ').replace(/-/g, '/')).getTime()
  if (!Number.isFinite(start)) return ''
  const years = (Date.now() - start) / (365.25 * 24 * 3600 * 1000)
  if (years < 0.05) return '新加入'
  return `${years.toFixed(1).replace(/\.0$/, '')}年`
}

function initial(name) {
  return (name || '用').slice(0, 1)
}

function formatDay(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 10)
}

async function loadOwnedBars() {
  const userId = idStr(route.params.userId)
  try {
    const page = await requestPage(`/bar/owned/page?userId=${userId}&pageNum=1&pageSize=50`, {
      token: auth.loggedIn ? auth.accessToken : undefined,
    })
    ownedBars.value = page.records || []
    ownedFanTotal.value = ownedBars.value.reduce((sum, bar) => sum + (Number(bar.followCount) || 0), 0)
  } catch {
    ownedBars.value = []
    ownedFanTotal.value = null
  }
}

async function loadFollowCount() {
  if (!auth.loggedIn) {
    followTotal.value = null
    followBars.value = []
    return
  }
  try {
    const page = await requestPage(
      `/follow/page?userId=${idStr(route.params.userId)}&pageNum=1&pageSize=20`,
      { token: auth.accessToken },
    )
    followTotal.value = Number(page.total) || 0
    followBars.value = page.records || []
    relationError.value = ''
  } catch (e) {
    followTotal.value = null
    relationError.value = e.message || '关注列表加载失败'
  }
}

async function loadBarFans(bar, append) {
  const barId = idStr(bar?.barId)
  if (!barId) return
  fanBarId.value = barId
  fanBarName.value = bar.name || '贴吧'
  const pageNum = append ? fanPageNum.value + 1 : 1
  relationLoading.value = true
  relationError.value = ''
  try {
    const page = await requestPage(
      `/follow/bar/${barId}/fans/page?pageNum=${pageNum}&pageSize=20`,
      { token: auth.accessToken },
    )
    const records = page.records || []
    fanUsers.value = append ? fanUsers.value.concat(records) : records
    fanPageNum.value = pageNum
    fanHasMore.value = fanUsers.value.length < (Number(page.total) || 0)
  } catch (e) {
    relationError.value = e.message || '粉丝列表加载失败'
  } finally {
    relationLoading.value = false
  }
}

async function openRelation(kind) {
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  relation.value = kind
  relationError.value = ''
  fanUsers.value = []
  fanBarId.value = ''
  fanBarName.value = ''
  relationLoading.value = true
  try {
    if (kind === 'follow') {
      await loadFollowCount()
    } else if (kind === 'fans') {
      await loadOwnedBars()
    }
  } finally {
    relationLoading.value = false
  }
}

function openUser(userId) {
  const id = idStr(userId)
  if (!id) return
  relation.value = ''
  router.push({ name: 'user', params: { userId: id } })
}

function openBar(bar) {
  const barId = idStr(bar?.barId)
  if (!barId) return
  router.push({ name: 'bar', params: { barId } })
}

async function loadProfile() {
  const userId = idStr(route.params.userId)
  if (!userId) return
  loading.value = true
  error.value = ''
  profile.value = null
  try {
    const data = await request(`/user/profile/${userId}`, {
      token: auth.loggedIn ? auth.accessToken : undefined,
    })
    profile.value = data
    if (isSelf.value) auth.setProfile(data)
    loadFollowCount()
    loadOwnedBars()
  } catch (e) {
    try {
      const brief = await request(`/user/brief/${userId}`)
      profile.value = {
        ...brief,
        fans: 0,
        followee: 0,
        likeCount: 0,
        introduce: '',
        city: '',
      }
      loadFollowCount()
      loadOwnedBars()
    } catch {
      error.value = e.message || '用户不存在'
    }
  } finally {
    loading.value = false
  }
}

async function loadPosts(append) {
  const userId = idStr(route.params.userId)
  listLoading.value = true
  listError.value = ''
  try {
    const cursor = append ? nextCursor.value : ''
    const query = new URLSearchParams({ userId, pageSize: '20' })
    if (cursor) query.set('lastId', cursor)
    const feed = await request(`/post/feed?${query.toString()}`, {
      token: auth.loggedIn ? auth.accessToken : undefined,
    })
    const records = feed?.records || []
    posts.value = append ? posts.value.concat(records) : records
    nextCursor.value = records.length && feed?.nextCursor ? idStr(feed.nextCursor) : ''
  } catch (e) {
    listError.value = e.message || '帖子加载失败'
  } finally {
    listLoading.value = false
  }
}

async function loadReplies(append) {
  if (!auth.loggedIn) {
    replies.value = []
    replyCursor.value = ''
    replyHasMore.value = false
    listError.value = ''
    return
  }
  listLoading.value = true
  listError.value = ''
  if (!append) {
    replyCursor.value = ''
  }
  const lastId = append ? replyCursor.value : ''
  try {
    const body = {
      userId: idStr(route.params.userId),
      pageSize: 20,
    }
    if (lastId) body.lastId = lastId
    const page = await request('/comment/user/page', {
      method: 'POST',
      token: auth.accessToken,
      body,
    })
    const records = page?.records || []
    replies.value = append ? replies.value.concat(records) : records
    replyCursor.value = page?.nextCursor ? idStr(page.nextCursor) : ''
    replyHasMore.value = Boolean(replyCursor.value)
  } catch (e) {
    listError.value = e.message || '回贴加载失败'
  } finally {
    listLoading.value = false
  }
}

async function loadInteraction(path, append = false) {
  if (!isSelf.value || !auth.loggedIn) {
    posts.value = []
    nextCursor.value = ''
    interactionHasMore.value = false
    listError.value = ''
    return
  }
  listLoading.value = true
  listError.value = ''
  const pageNum = append ? interactionPageNum.value + 1 : 1
  try {
    const userId = idStr(route.params.userId)
    const page = await requestPage(
      `${path}?userId=${userId}&pageNum=${pageNum}&pageSize=20`,
      { token: auth.accessToken },
    )
    const records = page.records || []
    posts.value = append ? posts.value.concat(records) : records
    interactionPageNum.value = pageNum
    interactionTotal.value = Number(page.total) || 0
    interactionHasMore.value = posts.value.length < interactionTotal.value
    nextCursor.value = ''
  } catch (e) {
    if (!append) posts.value = []
    listError.value = e.message || '加载失败'
  } finally {
    listLoading.value = false
  }
}

function loadTab() {
  posts.value = []
  replies.value = []
  nextCursor.value = ''
  interactionPageNum.value = 1
  interactionHasMore.value = false
  interactionTotal.value = 0
  listError.value = ''
  if (tab.value === 'likes' || tab.value === 'favorites') {
    if (!isSelf.value) {
      tab.value = 'posts'
      loadPosts(false)
      return
    }
  }
  if (tab.value === 'posts') loadPosts(false)
  else if (tab.value === 'replies') loadReplies(false)
  else if (tab.value === 'likes') loadInteraction('/thumb/page', false)
  else if (tab.value === 'favorites') loadInteraction('/favorite/page', false)
}

function openReplyPost(item) {
  const postId = idStr(item?.postId)
  if (!postId) return
  router.push({
    name: 'post',
    params: { postId },
    query: { comments: '1' },
    state: {
      summary: {
        postId,
        title: item.postTitle || '',
        barName: item.barName || '',
      },
    },
  })
}

function openPost(post, focusComments = false) {
  const postId = idStr(post?.postId)
  if (!postId) return
  router.push({
    name: 'post',
    params: { postId },
    query: focusComments ? { comments: '1' } : {},
    state: {
      summary: {
        postId,
        userId: idStr(post.userId || profile.value?.userId),
        nickName: post.nickName || profile.value?.nickName || '',
        icon: post.icon || profile.value?.icon || '',
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

function onSaved(next) {
  profile.value = { ...profile.value, ...next }
  showEdit.value = false
}

function openEditPost(post, event) {
  event?.stopPropagation?.()
  if (!isSelf.value) return
  if (!auth.loggedIn) {
    showLogin.value = true
    return
  }
  editPostId.value = idStr(post.postId)
  editPostInitial.value = {
    barId: post.barId,
    barName: post.barName,
    title: post.title,
    content: post.content || post.contentPreview || '',
    images: post.images?.length ? post.images : (post.coverImage ? [post.coverImage] : []),
  }
}

function closeEditPost() {
  editPostId.value = ''
  editPostInitial.value = null
}

function onPostSaved() {
  closeEditPost()
  if (tab.value === 'posts') loadPosts(false)
}

async function deletePost(post, event) {
  event?.stopPropagation?.()
  if (!isSelf.value) return
  const postId = idStr(post?.postId)
  if (!postId) return
  if (!window.confirm('确定删除这篇帖子？')) return
  deletingPostId.value = postId
  try {
    await request(`/post/${postId}`, {
      method: 'DELETE',
      token: auth.accessToken,
    })
    posts.value = posts.value.filter((p) => idStr(p.postId) !== postId)
  } catch (e) {
    listError.value = e.message || '删除失败'
  } finally {
    deletingPostId.value = ''
  }
}

watch(() => route.params.userId, () => {
  loadProfile()
  if ((tab.value === 'likes' || tab.value === 'favorites') && !isSelf.value) {
    tab.value = 'posts'
  }
  loadTab()
}, { immediate: true })

watch(tab, loadTab)

watch(isSelf, (self) => {
  if (!self && (tab.value === 'likes' || tab.value === 'favorites')) {
    tab.value = 'posts'
  }
})

watch(
  () => auth.loggedIn,
  () => {
    if (isSelf.value) loadTab()
  },
)
</script>

<template>
  <div class="page">
    <main class="wrap">
      <p v-if="error" class="banner error">{{ error }}</p>
      <p v-else-if="loading" class="banner">加载中…</p>

      <div v-else-if="profile" class="layout">
        <div class="center">
          <div class="hero-row">
            <button type="button" class="back" aria-label="返回首页" @click="router.push({ name: 'home' })">
              <img :src="iconBack" alt="" />
            </button>
            <section class="hero">
              <span class="avatar">
                <img v-if="profile.icon" :src="profile.icon" alt="" />
                <span v-else>{{ initial(profile.nickName) }}</span>
              </span>
              <div class="who">
                <h1>{{ profile.nickName || '用户' }}</h1>
                <p class="meta">
                  <span>ID {{ profile.userId }}</span>
                  <span v-if="barAge(profile.createTime)">吧龄 {{ barAge(profile.createTime) }}</span>
                  <span v-if="profile.city">{{ profile.city }}</span>
                </p>
                <p class="stats">
                  <button type="button" @click="openRelation('follow')">
                    <b>{{ formatCount(shownFollow) }}</b> 关注
                  </button>
                  <button type="button" @click="openRelation('fans')">
                    <b>{{ formatCount(shownFans) }}</b> 粉丝
                  </button>
                </p>
              </div>
              <button v-if="isSelf" type="button" class="edit" @click="showEdit = true">编辑资料</button>
            </section>
          </div>

          <div class="tabs">
            <button
              v-for="item in visibleTabs"
              :key="item.id"
              type="button"
              :class="{ on: tab === item.id }"
              @click="tab = item.id"
            >
              {{ item.label }}
            </button>
          </div>

          <p v-if="listError" class="empty">{{ listError }}</p>
          <p v-else-if="listLoading" class="empty">加载中…</p>
          <div v-else-if="tab === 'replies' && !auth.loggedIn" class="empty">
            <p>登录后查看回贴</p>
            <button type="button" class="login" @click="showLogin = true">登录</button>
          </div>
          <template v-else-if="tab === 'replies'">
            <div v-if="!replies.length" class="empty">
              <p>还没有发表过评论</p>
            </div>
            <article
              v-for="item in replies"
              :key="item.commentId"
              class="post reply-item"
              @click="openReplyPost(item)"
            >
              <p v-if="item.barName || item.postTitle" class="reply-meta">
                <span v-if="item.barName">{{ item.barName }}</span>
                <span v-if="item.postTitle"> · {{ item.postTitle }}</span>
              </p>
              <p v-if="item.replyNickName" class="reply-to">回复 {{ item.replyNickName }}</p>
              <p class="reply-content">{{ item.content || (item.imageUrl ? '[图片]' : '') }}</p>
              <footer>
                <span v-if="formatDay(item.createTime)">{{ formatDay(item.createTime) }}</span>
                <span>赞 {{ item.likeCount ?? 0 }}</span>
              </footer>
            </article>
            <button v-if="replyHasMore" type="button" class="more" @click="loadReplies(true)">
              加载更多
            </button>
          </template>
          <div v-else-if="(tab === 'likes' || tab === 'favorites') && !auth.loggedIn" class="empty">
            <p>登录后查看{{ tab === 'likes' ? '点赞' : '收藏' }}</p>
            <button type="button" class="login" @click="showLogin = true">登录</button>
          </div>
          <div v-else-if="(tab === 'posts' || tab === 'likes' || tab === 'favorites') && !posts.length" class="empty">
            <p>{{ emptyTabHint }}</p>
          </div>
          <template v-if="tab === 'posts' || tab === 'likes' || tab === 'favorites'">
          <article v-for="post in posts" :key="post.postId" class="post" @click="openPost(post)">
            <header>
              <span class="post-avatar">
                <img v-if="post.icon || profile.icon" :src="post.icon || profile.icon" alt="" />
                <span v-else>{{ initial(post.nickName || profile.nickName) }}</span>
              </span>
              <div>
                <div class="post-name">{{ post.nickName || profile.nickName || '用户' }}</div>
                <div v-if="formatDay(post.createTime)" class="post-time">{{ formatDay(post.createTime) }}</div>
              </div>
            </header>
            <div class="post-head">
              <h2>{{ post.title }}</h2>
              <div v-if="isSelf && tab === 'posts'" class="post-manage">
                <button type="button" @click="openEditPost(post, $event)">编辑</button>
                <button
                  type="button"
                  class="danger"
                  :disabled="deletingPostId === idStr(post.postId)"
                  @click="deletePost(post, $event)"
                >
                  {{ deletingPostId === idStr(post.postId) ? '删除中…' : '删除' }}
                </button>
              </div>
            </div>
            <p v-if="post.contentPreview">{{ post.contentPreview }}</p>
            <img v-if="post.coverImage" class="cover" :src="post.coverImage" alt="" />
            <footer>
              <span v-if="post.barName">{{ post.barName }}</span>
              <button type="button" class="count-btn" @click.stop="openPost(post, true)">
                <img class="act" :src="iconComment" alt="" /> {{ post.commentCount ?? 0 }}
              </button>
              <span><img class="act" :src="post.liked ? iconUnlike : iconLike" alt="" /> {{ post.likeCount ?? 0 }}</span>
              <span><img class="act" :src="post.favorited ? iconUnfavorite : iconFavorite" alt="" /> {{ post.favoriteCount ?? 0 }}</span>
            </footer>
          </article>
          <button v-if="tab === 'posts' && nextCursor" type="button" class="more" @click="loadPosts(true)">
            加载更多
          </button>
          <button
            v-if="(tab === 'likes' || tab === 'favorites') && interactionHasMore"
            type="button"
            class="more"
            @click="loadInteraction(tab === 'likes' ? '/thumb/page' : '/favorite/page', true)"
          >
            加载更多
          </button>
          </template>
        </div>

        <aside>
          <section class="card">
            <h3>我的简介</h3>
            <p>{{ profile.introduce || '生动有趣的个人简介更容易受到关注' }}</p>
          </section>
        </aside>
      </div>
    </main>

    <div v-if="relation" class="mask" @click.self="relation = ''">
      <section class="sheet">
        <header>
          <h2>{{ relation === 'follow' ? '关注' : '粉丝' }} {{ relation === 'follow' ? shownFollow : shownFans }}</h2>
          <button type="button" class="sheet-close" aria-label="关闭" @click="relation = ''">×</button>
        </header>
        <p v-if="relationError" class="sheet-empty">{{ relationError }}</p>
        <p v-else-if="relation === 'follow' && relationLoading" class="sheet-empty">加载中…</p>
        <template v-else-if="relation === 'follow'">
          <p v-if="!followBars.length" class="sheet-empty">还没有关注</p>
          <button v-for="bar in followBars" :key="bar.barId" type="button" class="bar" @click="openBar(bar)">
            <img v-if="bar.avatar" :src="bar.avatar" alt="" />
            <span v-else class="bar-fallback">{{ (bar.name || '吧').slice(0, 1) }}</span>
            <span class="bar-name">{{ bar.name }}</span>
            <span class="bar-meta">{{ formatCount(bar.followCount) }} 粉丝</span>
          </button>
        </template>
        <template v-else-if="relation === 'fans'">
          <p v-if="relationLoading && !fanBarId" class="sheet-empty">加载中…</p>
          <template v-else-if="fanBarId">
            <p class="sheet-sub">「{{ fanBarName }}」的粉丝</p>
            <p v-if="!fanUsers.length && !relationLoading" class="sheet-empty">还没有粉丝</p>
            <button
              v-for="user in fanUsers"
              :key="user.userId"
              type="button"
              class="bar user-row"
              @click="openUser(user.userId)"
            >
              <img v-if="user.icon" :src="user.icon" alt="" />
              <span v-else class="bar-fallback">{{ (user.nickName || '用').slice(0, 1) }}</span>
              <span class="bar-name">{{ user.nickName || '用户' }}</span>
            </button>
            <button v-if="fanHasMore" type="button" class="sheet-more" :disabled="relationLoading" @click="loadBarFans({ barId: fanBarId, name: fanBarName }, true)">
              {{ relationLoading ? '加载中…' : '加载更多' }}
            </button>
            <button type="button" class="sheet-back" @click="fanBarId = ''">返回吧列表</button>
          </template>
          <template v-else>
            <p v-if="!ownedBars.length" class="sheet-empty">
              {{ shownFans ? '暂无创建的贴吧' : '还没有吧粉' }}
            </p>
            <p v-else class="sheet-sub">我创建的吧 · 点击查看粉丝</p>
            <button
              v-for="bar in ownedBars"
              :key="bar.barId"
              type="button"
              class="bar"
              @click="loadBarFans(bar, false)"
            >
              <img v-if="bar.avatar" :src="bar.avatar" alt="" />
              <span v-else class="bar-fallback">{{ (bar.name || '吧').slice(0, 1) }}</span>
              <span class="bar-name">{{ bar.name }}</span>
              <span class="bar-meta">{{ formatCount(bar.followCount) }} 粉丝</span>
            </button>
          </template>
        </template>
      </section>
    </div>

    <ProfileEditDialog v-if="showEdit && profile" :profile="profile" @close="showEdit = false" @saved="onSaved" />
    <PostDialog
      v-if="editPostId"
      :post-id="editPostId"
      :initial="editPostInitial"
      @close="closeEditPost"
      @need-login="showLogin = true"
      @success="onPostSaved"
    />
    <LoginDialog v-if="showLogin" @close="showLogin = false" />
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f6f7fb;
  color: #1a1a1a;
}
.wrap {
  max-width: 1080px;
  margin: 0 auto;
  padding: 18px 20px 48px;
}
.banner {
  text-align: center;
  color: #9aa0a6;
  padding: 40px 0;
}
.banner.error {
  color: #dc2626;
}
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;
}
.hero-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
}
.back {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: grid;
  place-items: center;
}
.back img {
  width: 20px;
  height: 20px;
  object-fit: contain;
}
.hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 132px;
  padding: 22px 24px;
  border-radius: 16px;
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 18%, rgba(255, 255, 255, 0.95) 0 36px, transparent 37px),
    radial-gradient(circle at 96% 70%, rgba(186, 214, 255, 0.55), transparent 42%),
    linear-gradient(180deg, #e7f3ff 0%, #f3f8ff 100%);
}
.avatar,
.post-avatar {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  overflow: hidden;
  background: #d7e6fb;
  color: #7aa2e3;
  display: grid;
  place-items: center;
  font-size: 26px;
  flex-shrink: 0;
}
.avatar img,
.post-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.who {
  min-width: 0;
  flex: 1;
  position: relative;
  z-index: 1;
}
h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin: 8px 0 0;
  color: #9aa1ab;
  font-size: 12px;
}
.stats {
  display: flex;
  gap: 18px;
  margin: 14px 0 0;
  color: #8b919a;
  font-size: 13px;
}
.stats button {
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  padding: 0;
}
.stats b {
  color: #222;
  font-size: 16px;
  margin-right: 4px;
}
.edit {
  position: relative;
  z-index: 1;
  align-self: flex-start;
  border: 1px solid #e6e8ee;
  background: #fff;
  color: #4b5160;
  border-radius: 18px;
  padding: 6px 16px;
  cursor: pointer;
  font-size: 14px;
}
.tabs {
  display: flex;
  gap: 32px;
  padding: 16px 8px 0 40px;
}
.tabs button {
  border: none;
  background: transparent;
  padding: 8px 0 10px;
  color: #8b919a;
  cursor: pointer;
  position: relative;
  font-size: 16px;
}
.tabs button.on {
  color: #1a1a1a;
  font-weight: 700;
}
.tabs button.on::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: #4c8dff;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 72px 16px;
}
.login {
  margin-top: 12px;
  border: none;
  background: #3b6cff;
  color: #fff;
  border-radius: 16px;
  padding: 6px 18px;
  cursor: pointer;
}
.post {
  margin: 12px 0 0 40px;
  padding: 14px 16px 12px;
  border-radius: 12px;
  background: #f4f5f7;
  cursor: pointer;
}
.post header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.post-avatar {
  width: 36px;
  height: 36px;
  font-size: 14px;
}
.post-name {
  font-size: 14px;
  font-weight: 700;
}
.post-time {
  margin-top: 2px;
  color: #b0b4bd;
  font-size: 12px;
}
.post-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.post h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.55;
  flex: 1;
  min-width: 0;
}
.post-manage {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.post-manage button {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #4b5563;
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
}
.post-manage button.danger {
  color: #dc2626;
  border-color: #fecaca;
}
.post-manage button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.post p {
  margin: 8px 0 0;
  color: #4b5160;
  line-height: 1.55;
}
.cover {
  display: block;
  width: 220px;
  max-width: 100%;
  margin-top: 10px;
  border-radius: 8px;
}
.post footer {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  color: #9aa1ab;
  font-size: 13px;
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
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.more {
  display: block;
  margin: 16px auto 8px 40px;
  border: none;
  background: transparent;
  color: #4c8dff;
  cursor: pointer;
}
.card {
  background: #fff;
  border: 1px solid #eceef2;
  border-radius: 12px;
  padding: 16px 16px 18px;
}
.card h3 {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
}
.card p {
  margin: 0;
  color: #9aa1ab;
  font-size: 14px;
  line-height: 1.6;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.45);
  display: grid;
  place-items: center;
  z-index: 20;
  padding: 24px 16px;
}
.sheet {
  width: min(420px, 100%);
  max-height: min(560px, calc(100vh - 48px));
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  padding: 16px 16px 20px;
}
.sheet header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sheet h2 {
  margin: 0;
  font-size: 18px;
}
.sheet-close {
  border: none;
  background: transparent;
  font-size: 24px;
  cursor: pointer;
}
.sheet-empty {
  text-align: center;
  color: #9ca3af;
  padding: 28px 8px;
}
.sheet-sub {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 13px;
}
.sheet-more,
.sheet-back {
  display: block;
  width: 100%;
  margin-top: 8px;
  border: none;
  background: transparent;
  color: #4c8dff;
  cursor: pointer;
  padding: 8px;
}
.reply-item .reply-meta {
  margin: 0;
  color: #9aa1ab;
  font-size: 12px;
}
.reply-to {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.reply-content {
  margin: 8px 0 0;
  line-height: 1.55;
  color: #222;
}
.bar {
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
.bar img,
.bar-fallback {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  background: #e8eef8;
  color: #3b6cff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.bar-name {
  flex: 1;
  font-size: 15px;
  color: #111827;
}
.bar-meta {
  color: #9ca3af;
  font-size: 12px;
}
@media (max-width: 800px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .post,
  .more,
  .tabs {
    margin-left: 0;
    padding-left: 0;
  }
  .post {
    margin-left: 0;
  }
}
</style>
