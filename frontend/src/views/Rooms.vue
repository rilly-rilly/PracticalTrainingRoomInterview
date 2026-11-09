<script setup>
import { computed, onMounted, ref } from 'vue'
import { publicApi } from '../api'
import FeedbackState from '../components/FeedbackState.vue'

const loading = ref(true)
const error = ref(null)
const rooms = ref([])

const isEmpty = computed(() => !loading.value && !error.value && !rooms.value.length)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await publicApi.rooms()
    rooms.value = res.data || []
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-head">
      <div>
        <h1>实训室介绍</h1>
        <p class="page-head__sub">实训室介绍供游客浏览，正式名额以管理员统筹为准。</p>
      </div>
      <div class="page-head__actions">
        <router-link to="/" class="btn btn--ghost btn--sm">返回首页</router-link>
        <router-link to="/login" class="btn btn--sm">登录系统</router-link>
      </div>
    </div>

    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="isEmpty"
      loading-text="正在加载实训室列表…"
      empty-text="暂无可展示的实训室，请等待管理员配置"
      @retry="load"
    >
      <div class="grid grid--2">
        <article v-for="room in rooms" :key="room.id" class="room-card">
          <div class="room-card__top">
            <div>
              <div class="room-card__code">{{ room.code }}</div>
              <h3>{{ room.name }}</h3>
              <span class="room-card__direction">{{ room.direction }}</span>
            </div>
            <span class="badge" :class="room.remaining > 0 ? 'badge--admitted' : 'badge--rejected'">
              {{ room.remaining > 0 ? `剩余 ${room.remaining} 个名额` : '已招满' }}
            </span>
          </div>

          <div class="tag-row">
            <span v-for="tag in room.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>

          <div class="room-card__meta">
            <div>
              招收名额<strong>{{ room.quota }}</strong>
            </div>
            <div>
              剩余名额<strong>{{ room.remaining }}</strong>
            </div>
            <div>
              导师人数<strong>{{ room.teacherCount }}</strong>
            </div>
            <div>
              已占用名额<strong>{{ room.quota - room.remaining }}</strong>
            </div>
          </div>

          <div class="room-card__text"><strong>培养安排：</strong>{{ room.training }}</div>
          <div class="room-card__text"><strong>考核方式：</strong>{{ room.assessment }}</div>
          <div class="room-card__text"><strong>实训室简介：</strong>{{ room.description }}</div>
        </article>
      </div>
    </FeedbackState>
  </main>
</template>
