<script setup>
import { computed, reactive, ref } from 'vue'
import { request, uploadFile } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const NICK_MAX = 32
const INTRO_MAX = 128

const props = defineProps({
  profile: { type: Object, required: true },
})
const emit = defineEmits(['close', 'saved'])

const auth = useAuthStore()
const fileRef = ref(null)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')

const form = reactive({
  nickName: props.profile.nickName || '',
  icon: props.profile.icon || '',
  introduce: props.profile.introduce || '',
  gender: props.profile.gender === 0 || props.profile.gender === 1 ? String(props.profile.gender) : '',
})

const nickCount = computed(() => form.nickName.length)
const introCount = computed(() => form.introduce.length)
const accountName = computed(() => auth.phone || props.profile.userId || '')

function pickAvatar() {
  fileRef.value?.click()
}

async function onAvatar(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    form.icon = await uploadFile('/user/avatar', file, auth.accessToken)
  } catch (e) {
    error.value = e.message || '头像上传失败'
  } finally {
    uploading.value = false
  }
}

async function save() {
  const nickName = form.nickName.trim()
  if (!nickName) {
    error.value = '请填写昵称'
    return
  }
  if (nickName.length > NICK_MAX) {
    error.value = `昵称不能超过 ${NICK_MAX} 个字`
    return
  }
  if (form.introduce.length > INTRO_MAX) {
    error.value = `简介不能超过 ${INTRO_MAX} 个字`
    return
  }
  saving.value = true
  error.value = ''
  const body = {
    nickName,
    icon: form.icon || '',
    introduce: form.introduce.trim(),
  }
  if (form.gender === '0' || form.gender === '1') {
    body.gender = Number(form.gender)
  }
  try {
    await request('/user/profile', {
      method: 'PUT',
      token: auth.accessToken,
      body,
    })
    const next = {
      ...props.profile,
      ...body,
      gender: body.gender ?? props.profile.gender,
    }
    auth.setProfile(next)
    emit('saved', next)
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <form class="dialog" @submit.prevent="save">
      <header>
        <h2>编辑资料</h2>
        <button type="button" class="close" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <button type="button" class="avatar" aria-label="更换头像" @click="pickAvatar">
        <img v-if="form.icon" :src="form.icon" alt="" />
        <svg v-else viewBox="0 0 80 80" aria-hidden="true">
          <circle cx="40" cy="40" r="40" fill="#d9e8ff" />
          <circle cx="40" cy="32" r="12" fill="#fff" />
          <path d="M18 66c4-12 14-18 22-18s18 6 22 18" fill="#fff" />
        </svg>
        <span v-if="uploading" class="uploading">上传中…</span>
      </button>
      <input ref="fileRef" class="file" type="file" accept="image/*" @change="onAvatar" />

      <div class="field">
        <span>用户名</span>
        <p class="account">{{ accountName }}</p>
      </div>

      <label class="field">
        <span>昵称</span>
        <span class="control">
          <input v-model="form.nickName" :maxlength="NICK_MAX" />
          <em :class="{ over: nickCount > NICK_MAX }">{{ nickCount }} / {{ NICK_MAX }}</em>
        </span>
      </label>

      <div class="field">
        <span>性别</span>
        <span class="radios">
          <label><input v-model="form.gender" type="radio" value="0" /> 男</label>
          <label><input v-model="form.gender" type="radio" value="1" /> 女</label>
        </span>
      </div>

      <label class="field top">
        <span>简介</span>
        <span class="control area">
          <textarea v-model="form.introduce" :maxlength="INTRO_MAX" rows="4" placeholder="填写简介更容易获得关注哦～" />
          <em>{{ introCount }}/{{ INTRO_MAX }}</em>
        </span>
      </label>

      <p v-if="error" class="error">{{ error }}</p>
      <button class="save" type="submit" :disabled="saving || uploading">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.45);
  display: grid;
  place-items: center;
  z-index: 20;
  padding: 24px 16px;
}
.dialog {
  width: min(520px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  background: #fff;
  border-radius: 16px;
  padding: 22px 28px 28px;
  box-sizing: border-box;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.close {
  border: none;
  background: transparent;
  font-size: 26px;
  line-height: 1;
  color: #111;
  cursor: pointer;
}
.avatar {
  position: relative;
  width: 104px;
  height: 104px;
  margin: 18px auto 22px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  display: block;
}
.avatar img,
.avatar svg {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.uploading {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.72);
  display: grid;
  place-items: center;
  font-size: 13px;
  color: #3b6cff;
}
.file {
  display: none;
}
.field {
  display: grid;
  grid-template-columns: 64px 1fr;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  color: #222;
  font-size: 15px;
}
.field.top {
  align-items: flex-start;
}
.field.top > span:first-child {
  padding-top: 10px;
}
.account {
  margin: 0;
  color: #111;
}
.control {
  position: relative;
  display: block;
}
.control input,
.control textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e6e8ee;
  border-radius: 8px;
  background: #fff;
  font: inherit;
  color: #111;
  padding: 10px 72px 10px 12px;
}
.control textarea {
  resize: none;
  min-height: 108px;
  padding-bottom: 28px;
}
.control textarea::placeholder {
  color: #c5c8d0;
}
.control em {
  position: absolute;
  right: 12px;
  bottom: 10px;
  font-style: normal;
  font-size: 13px;
  color: #b0b4bd;
}
.control input + em {
  top: 50%;
  bottom: auto;
  transform: translateY(-50%);
}
.control em.over {
  color: #ef4444;
}
.radios {
  display: flex;
  gap: 22px;
}
.radios label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.error {
  color: #dc2626;
  font-size: 13px;
  margin: 0 0 8px;
  text-align: center;
}
.save {
  display: block;
  width: 148px;
  height: 40px;
  margin: 8px auto 0;
  border: none;
  border-radius: 20px;
  background: #2f7bff;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
}
.save:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
