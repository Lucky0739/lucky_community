<script setup>
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits(['close'])
const auth = useAuthStore()

const mode = ref('login')
const form = reactive({
  phone: '',
  password: '',
})
const error = ref('')
const hint = ref('')
const submitting = ref(false)

function validate() {
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) {
    error.value = '请输入正确的手机号'
    return false
  }
  if (!form.password || form.password.length < 6) {
    error.value = '密码至少 6 位'
    return false
  }
  return true
}

async function submit() {
  error.value = ''
  hint.value = ''
  if (!validate()) {
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'register') {
      await auth.register(form.phone.trim(), form.password)
      mode.value = 'login'
      hint.value = '注册成功，请登录'
      form.password = ''
      return
    }
    await auth.login(form.phone.trim(), form.password)
    emit('close')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    submitting.value = false
  }
}

function showRegister() {
  mode.value = 'register'
  error.value = ''
  hint.value = ''
}

function showLogin() {
  mode.value = 'login'
  error.value = ''
  hint.value = ''
}
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <form class="dialog" @submit.prevent="submit">
      <button type="button" class="close" aria-label="关闭" @click="emit('close')">×</button>

      <div class="brand">
        <span class="logo">贴</span>
        <span class="title">幸运贴吧</span>
      </div>
      <p class="slogan">登录贴吧，解锁更多功能</p>

      <div class="form-fields">
        <input
          v-model="form.phone"
          class="field"
          type="tel"
          maxlength="11"
          placeholder="手机号"
          autocomplete="username"
        />
        <input
          v-model="form.password"
          class="field"
          type="password"
          maxlength="32"
          placeholder="密码"
          autocomplete="current-password"
        />
        <button v-if="mode === 'login'" type="button" class="forgot" @click="hint = '请使用注册手机号登录，或先注册新账号'">
          忘记密码?
        </button>
        <p v-if="hint" class="hint">{{ hint }}</p>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="submit" type="submit" :disabled="submitting">
          {{ submitting ? '请稍候…' : mode === 'register' ? '注册' : '登录' }}
        </button>
      </div>

      <div class="foot">
        <button v-if="mode === 'login'" type="button" class="link muted" @click="showRegister">立即注册</button>
        <button v-else type="button" class="link muted" @click="showLogin">返回登录</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
}
.dialog {
  position: relative;
  width: 440px;
  max-width: calc(100vw - 32px);
  padding: 28px 36px 22px;
  border-radius: 16px;
  background: #2b2d31;
  color: #fff;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.45);
}
.close {
  position: absolute;
  top: 16px;
  right: 18px;
  border: none;
  background: transparent;
  color: #6d7dff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
}
.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
}
.logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #fff;
  color: #4f6bff;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 18px;
}
.title {
  font-size: 28px;
  font-weight: 800;
  color: #4f6bff;
  letter-spacing: 1px;
}
.slogan {
  margin: 10px 0 22px;
  text-align: center;
  color: #4f6bff;
  font-size: 16px;
}
.field {
  width: 100%;
  height: 52px;
  margin-bottom: 14px;
  padding: 0 18px;
  border: none;
  border-radius: 12px;
  background: #141518;
  color: #fff;
  font-size: 15px;
}
.field::placeholder {
  color: #8b909a;
}
.field:focus {
  outline: 1px solid #4f6bff;
}
.forgot {
  display: block;
  margin: -4px 0 12px auto;
  border: none;
  background: transparent;
  color: #b7bcc6;
  font-size: 14px;
  cursor: pointer;
}
.hint,
.error {
  margin: 0 0 10px;
  font-size: 13px;
}
.hint {
  color: #93c5fd;
}
.error {
  color: #fca5a5;
}
.submit {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  border: none;
  border-radius: 12px;
  background: #5b7cff;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
}
.submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.foot {
  display: flex;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid #3e424a;
}
.link {
  border: none;
  background: transparent;
  font-size: 15px;
  padding: 0;
}
.muted {
  color: #d1d5db;
  cursor: pointer;
}
</style>
