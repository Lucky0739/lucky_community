<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { request, requestPage } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const tab = ref('users')
const error = ref('')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10

const phone = ref('')
const nickName = ref('')
const postBarId = ref('')
const postPending = ref(false)
const postFailed = ref(false)
const barKeyword = ref('')
const barStatus = ref('')
const appointBarId = ref('')

const tabs = [
  { id: 'users', label: '用户' },
  { id: 'posts', label: '帖子' },
  { id: 'bars', label: '贴吧' },
]
const reviewActions = ['未处置', '警告', '删帖', '封禁']

const pageCount = computed(() => Math.max(1, Math.ceil(Number(total.value) / pageSize)))

function roleText(user) {
  return (user.roles || []).map((role) => role.roleName || role.roleCode).join('、') || '普通用户'
}

async function load() {
  if (!auth.loggedIn) {
    error.value = '请先登录'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const token = auth.accessToken
    if (tab.value === 'users') {
      const query = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(pageSize) })
      if (phone.value.trim()) query.set('phone', phone.value.trim())
      if (nickName.value.trim()) query.set('nickName', nickName.value.trim())
      const page = await requestPage(`/admin/user/page?${query}`, { token })
      rows.value = page.records
      total.value = page.total
    } else if (tab.value === 'posts') {
      const query = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(pageSize) })
      if (postBarId.value.trim()) query.set('barId', postBarId.value.trim())
      if (postPending.value) query.set('pending', 'true')
      if (postFailed.value) query.set('analysisFailed', 'true')
      const page = await requestPage(`/admin/post/page?${query}`, { token })
      rows.value = page.records
      total.value = page.total
    } else {
      const query = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(pageSize) })
      if (barKeyword.value.trim()) query.set('keyword', barKeyword.value.trim())
      if (barStatus.value !== '') query.set('status', barStatus.value)
      const page = await requestPage(`/admin/bar/page?${query}`, { token })
      rows.value = page.records
      total.value = page.total
    }
  } catch (e) {
    rows.value = []
    total.value = 0
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function turn(delta) {
  const next = pageNum.value + delta
  if (next < 1 || next > pageCount.value) return
  pageNum.value = next
  load()
}

async function removeUser(user) {
  if (idStr(user.userId) === idStr(auth.userId)) {
    error.value = '不能删除当前登录的管理员'
    return
  }
  if (!window.confirm(`删除用户 ${user.nickName || user.userId}？`)) return
  try {
    await request('/admin/user', {
      method: 'DELETE',
      token: auth.accessToken,
      body: { userIds: [idStr(user.userId)] },
    })
    load()
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

async function appoint(user) {
  const barId = appointBarId.value.trim()
  if (!barId) {
    error.value = '先在上方填写要任命的贴吧 ID'
    return
  }
  try {
    await request('/admin/user/bar-admin/appoint', {
      method: 'POST',
      token: auth.accessToken,
      body: { barId, userId: idStr(user.userId) },
    })
    appointBarId.value = ''
    load()
  } catch (e) {
    error.value = e.message || '任命失败'
  }
}

async function reviewPost(post, handlerAction) {
  try {
    await request('/admin/post/review', {
      method: 'POST',
      token: auth.accessToken,
      body: { postId: idStr(post.postId), handlerAction },
    })
    load()
  } catch (e) {
    error.value = e.message || '审核失败'
  }
}

async function removePost(post) {
  if (!window.confirm(`删除帖子「${post.title || post.postId}」？`)) return
  try {
    await request(`/admin/post/${idStr(post.postId)}`, { method: 'DELETE', token: auth.accessToken })
    load()
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

async function reanalyze(post) {
  try {
    await request(`/admin/post/${idStr(post.postId)}/reanalyze`, { method: 'POST', token: auth.accessToken })
    error.value = ''
    load()
  } catch (e) {
    error.value = e.message || '重跑失败'
  }
}

async function setBarStatus(bar, status) {
  try {
    await request('/admin/bar/status', {
      method: 'PUT',
      token: auth.accessToken,
      body: { barId: idStr(bar.barId), status },
    })
    load()
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

async function removeBar(bar) {
  if (!window.confirm(`删除贴吧「${bar.name}」？`)) return
  try {
    await request(`/admin/bar/${idStr(bar.barId)}`, { method: 'DELETE', token: auth.accessToken })
    load()
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

watch(tab, () => {
  pageNum.value = 1
  rows.value = []
  load()
})

load()
</script>

<template>
  <div class="page">
    <header class="top">
      <button type="button" class="back" @click="router.push({ name: 'home' })">返回首页</button>
      <h1>管理后台</h1>
    </header>

    <main class="wrap">
      <div class="tabs">
        <button v-for="item in tabs" :key="item.id" type="button" :class="{ on: tab === item.id }" @click="tab = item.id">
          {{ item.label }}
        </button>
      </div>

      <form class="filters" @submit.prevent="search">
        <template v-if="tab === 'users'">
          <input v-model="phone" placeholder="手机号前缀" />
          <input v-model="nickName" placeholder="昵称" />
          <input v-model="appointBarId" placeholder="任命吧务时的贴吧 ID" />
        </template>
        <template v-else-if="tab === 'posts'">
          <input v-model="postBarId" placeholder="贴吧 ID，可空" />
          <label><input v-model="postPending" type="checkbox" /> 待人工审核</label>
          <label><input v-model="postFailed" type="checkbox" /> AI 分析失败</label>
        </template>
        <template v-else>
          <input v-model="barKeyword" placeholder="吧名前缀" />
          <select v-model="barStatus">
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="0">停用</option>
          </select>
        </template>
        <button type="submit">查询</button>
      </form>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="muted">加载中…</p>

      <table v-else-if="tab === 'users'">
        <thead>
          <tr>
            <th>用户</th>
            <th>手机号</th>
            <th>角色</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in rows" :key="user.userId">
            <td>
              <div class="name">{{ user.nickName || '未命名' }}</div>
              <div class="id">{{ user.userId }}</div>
            </td>
            <td>{{ user.phone || '—' }}</td>
            <td>{{ roleText(user) }}</td>
            <td class="ops">
              <button type="button" @click="appoint(user)">任命吧务</button>
              <button type="button" class="danger" @click="removeUser(user)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <table v-else-if="tab === 'posts'">
        <thead>
          <tr>
            <th>帖子</th>
            <th>贴吧 / 作者</th>
            <th>审核</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="post in rows" :key="post.postId">
            <td>
              <div class="name">{{ post.title }}</div>
              <div class="id">{{ post.contentPreview }}</div>
            </td>
            <td>
              <div>{{ post.barName }}</div>
              <div class="id">{{ post.nickName }}</div>
            </td>
            <td>
              <div>{{ post.riskLevel || '未分析' }}</div>
              <div class="id">{{ post.handlerAction || '未处置' }}</div>
            </td>
            <td class="ops">
              <button v-for="action in reviewActions" :key="action" type="button" @click="reviewPost(post, action)">
                {{ action }}
              </button>
              <button v-if="post.analysisStatus === 3" type="button" @click="reanalyze(post)">重跑审核</button>
              <button type="button" class="danger" @click="removePost(post)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <table v-else>
        <thead>
          <tr>
            <th>贴吧</th>
            <th>数据</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="bar in rows" :key="bar.barId">
            <td>
              <div class="name">{{ bar.name }}</div>
              <div class="id">{{ bar.barId }}</div>
            </td>
            <td>关注 {{ bar.followCount ?? 0 }} · 帖子 {{ bar.postCount ?? 0 }}</td>
            <td>{{ bar.status === 0 ? '停用' : '正常' }}</td>
            <td class="ops">
              <button v-if="bar.status !== 0" type="button" @click="setBarStatus(bar, 0)">停用</button>
              <button v-else type="button" @click="setBarStatus(bar, 1)">恢复</button>
              <button type="button" class="danger" @click="removeBar(bar)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-if="!loading && !rows.length" class="muted">没有数据</p>

      <div class="pager">
        <button type="button" :disabled="pageNum <= 1" @click="turn(-1)">上一页</button>
        <span>{{ pageNum }} / {{ pageCount }} · 共 {{ total }} 条</span>
        <button type="button" :disabled="pageNum >= pageCount" @click="turn(1)">下一页</button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f6f7fb;
  color: #1a1a1a;
}
.top {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.top h1 {
  margin: 0;
  font-size: 18px;
}
.back {
  border: none;
  background: transparent;
  color: #3b6cff;
  cursor: pointer;
}
.wrap {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px;
}
.tabs {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}
.tabs button {
  border: none;
  background: transparent;
  font-size: 16px;
  color: #6b7280;
  cursor: pointer;
  padding: 8px 0;
}
.tabs button.on {
  color: #111;
  font-weight: 700;
  border-bottom: 3px solid #3b6cff;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.filters input,
.filters select {
  height: 34px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0 10px;
  background: #fff;
}
.filters button,
.ops button,
.pager button {
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #e8f1ff;
  color: #2b6cff;
  cursor: pointer;
  padding: 0 10px;
}
.filters label {
  font-size: 14px;
  color: #4b5563;
}
table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
th,
td {
  text-align: left;
  padding: 12px;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: top;
  font-size: 14px;
}
.name {
  font-weight: 700;
}
.id {
  margin-top: 4px;
  color: #9aa0a6;
  font-size: 12px;
  word-break: break-all;
}
.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.danger {
  background: #fee2e2 !important;
  color: #dc2626 !important;
}
.error {
  color: #dc2626;
}
.muted {
  color: #9aa0a6;
  text-align: center;
}
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  color: #6b7280;
}
.pager button:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
