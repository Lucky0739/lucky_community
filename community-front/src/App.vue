<script setup>
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { RetroGrid } from 'retro-grid-vue'
import 'retro-grid-vue/style.css'
import { useAuthStore } from '@/stores/auth'
import './theme.css'

const auth = useAuthStore()

onMounted(() => {
  auth.restore()
})
</script>

<template>
  <div class="tech-stage">
    <RetroGrid
      theme="classic"
      :angle="68"
      line-color="rgba(47, 125, 220, 0.28)"
      :glow-size="6"
      :opacity="0.45"
      bg-gradient-from="#e7f2ff"
      bg-gradient-to="#f7fbff"
    />
    <div class="tech-app">
      <RouterView />
    </div>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
}
html,
body,
#app {
  margin: 0;
  min-height: 100%;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  background: #e7f2ff;
}
.tech-stage {
  position: relative;
  min-height: 100vh;
}
.tech-app {
  position: relative;
  z-index: 1;
}
</style>
