import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import BarDetailView from '@/views/BarDetailView.vue'
import PostDetailView from '@/views/PostDetailView.vue'
import UserProfileView from '@/views/UserProfileView.vue'
import AdminView from '@/views/AdminView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/bar/:barId', name: 'bar', component: BarDetailView },
    { path: '/post/:postId', name: 'post', component: PostDetailView },
    { path: '/profile/:userId', name: 'user', component: UserProfileView },
    { path: '/manage', name: 'admin', component: AdminView },
  ],
})

export default router
