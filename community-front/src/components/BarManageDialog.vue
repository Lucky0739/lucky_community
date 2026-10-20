<script setup>
import { computed, reactive, ref } from 'vue'
import { request, uploadFile } from '@/api/http'
import { idStr } from '@/utils/id'
import { useAuthStore } from '@/stores/auth'

const NAME_MAX = 64
const DESC_MAX = 255

const props = defineProps({
  /** 传入 bar 则为编辑，否则为创建 */
  bar: { type: Object, default: null },
})
const emit = defineEmits(['close', 'saved'])

const auth = useAuthStore()
const isEdit = computed(() => !!props.bar?.barId)
const fileRef = ref(null)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')

const form = reactive({
  name: props.bar?.name || '',
  description: props.bar?.description || '',
  avatar: props.bar?.avatar || '',
})

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
    form.avatar = await uploadFile('/bar/avatar', file, auth.accessToken)
  } catch (e) {
    error.value = e.message || '头像上传失败'
  } finally {
    uploading.value = false
  }
}

async function save() {
  const name = form.name.trim()
  if (!name) {
    error.value = '请填写吧名'
    return
  }
  if (name.length > NAME_MAX) {
    error.value = `吧名不能超过 ${NAME_MAX} 个字`
    return
  }
  if ((form.description || '').length > DESC_MAX) {
    error.value = `简介不能超过 ${DESC_MAX} 个字`
    return
  }
  saving.value = true
  error.value = ''
  try {
    const body = {
      name,
      description: form.description.trim(),
      avatar: form.avatar || '',
    }
    let saved
    if (isEdit.value) {
      saved = await request(`/bar/${idStr(props.bar.barId)}`, {
        method: 'PUT',
        token: auth.accessToken,
        body,
      })
    } else {
      saved = await request('/bar/create', {
        method: 'POST',
        token: auth.accessToken,
        body,
      })
    }
    emit('saved', saved)
    emit('close')
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
        <h2>{{ isEdit ? '编辑贴吧' : '创建贴吧' }}</h2>
        <button type="button" class="close" aria-label="关闭" @click="emit('close')">×</button>
      </header>

      <label class="field">
        <span>吧头像</span>
        <button type="button" class="avatar" @click="pickAvatar">
          <img v-if="form.avatar" :src="form.avatar" alt="" />
          <span v-else>{{ uploading ? '上传中…' : '上传' }}</span>
        </button>
        <input ref="fileRef" type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden @change="onAvatar" />
      </label>

      <label class="field">
        <span>吧名</span>
        <input v-model="form.name" type="text" :maxlength="NAME_MAX" placeholder="全站唯一吧名" />
      </label>

      <label class="field">
        <span>简介</span>
        <textarea v-model="form.description" :maxlength="DESC_MAX" rows="4" placeholder="介绍一下这个吧" />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <footer>
        <button type="submit" class="primary" :disabled="saving || uploading">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(17, 24, 39, 0.45);
  display: grid;
  place-items: center;
  padding: 24px;
}
.dialog {
  width: 480px;
  max-width: 100%;
  background: #fff;
  border-radius: 16px;
  padding: 18px 22px 22px;
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
h2 {
  margin: 0;
  font-size: 18px;
}
.close {
  border: none;
  background: transparent;
  font-size: 26px;
  cursor: pointer;
  color: #9ca3af;
}
.field {
  display: block;
  margin-bottom: 14px;
}
.field > span {
  display: block;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
input,
textarea {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  box-sizing: border-box;
}
.avatar {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  border: 1px dashed #d1d5db;
  background: #f9fafb;
  overflow: hidden;
  cursor: pointer;
  padding: 0;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.error {
  color: #dc2626;
  font-size: 13px;
}
footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.primary {
  border: none;
  background: #3b6cff;
  color: #fff;
  border-radius: 18px;
  padding: 8px 22px;
  cursor: pointer;
}
.primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>
