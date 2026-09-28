import { createRouter, createWebHistory } from 'vue-router'
import Landing from './views/Landing.vue'
import Dashboard from './views/Dashboard.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'landing',
      component: Landing,
    },
    {
      path: '/dashbaord',
      name: 'dashboard'
    }
  ],
})

export default router
