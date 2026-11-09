<script setup>
import { computed, onMounted, ref } from 'vue'
import { publicApi } from '../api'
import { authState, roleHome } from '../auth'
import { formatDateTime, windowStateText } from '../utils/format'
import FeedbackState from '../components/FeedbackState.vue'

const loading = ref(true)
const error = ref(null)
const data = ref({ steps: [], notices: [], rooms: [], intentWindow: null })

const levelBadges = { 重要: 'badge--rejected', 提醒: 'badge--pending', 普通: 'badge--givenup' }
const stateBadges = { 进行中: 'badge--admitted', 已结束: 'badge--confirmed', 未开始: 'badge--givenup' }

const totalQuota = computed(() => data.value.rooms.reduce((sum, room) => sum + Number(room.quota || 0), 0))
const activeSteps = computed(() => data.value.steps.filter((step) => step.state === '进行中').length)
const isEmpty = computed(
  () => !loading.value && !error.value && !data.value.steps.length && !data.value.notices.length && !data.value.rooms.length
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await publicApi.home()
    data.value = res.data || { steps: [], notices: [], rooms: [], intentWindow: null }
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
    <FeedbackState
      :loading="loading"
      :error="error"
      :empty="isEmpty"
      loading-text="正在加载首页数据…"
      empty-text="首页内容尚未配置，请稍后重试或联系管理员"
      @retry="load"
    >
      <header class="hero">
        <p class="hero__eyebrow">STARFIRE LAB · SELECTION DEMO</p>
        <h1>星火实训室 · 进组选拔系统</h1>
        <p>游客可浏览实训室与选拔流程；学生 / 导师 / 管理员登录后进入各自工作台，完成意向填报、面试考核、录取统筹与结果公示。</p>

        <div class="hero__actions">
          <template v-if="!authState.token">
            <router-link to="/login" class="btn btn--lg">登录系统</router-link>
            <router-link to="/rooms" class="btn btn--ghost btn--lg btn--on-dark">查看实训室介绍</router-link>
          </template>
          <router-link v-else :to="roleHome(authState.role)" class="btn btn--lg">进入我的工作台</router-link>
        </div>

        <div class="hero__stats">
          <div class="hero__stat">
            <strong>{{ data.rooms.length }}</strong>
            <span>实训室数量</span>
          </div>
          <div class="hero__stat">
            <strong>{{ totalQuota }}</strong>
            <span>名额总数</span>
          </div>
          <div class="hero__stat">
            <strong>{{ activeSteps }}</strong>
            <span>进行中流程步骤</span>
          </div>
          <div class="hero__stat">
            <strong>{{ windowStateText(data.intentWindow) }}</strong>
            <span>意向填报窗口</span>
          </div>
        </div>
      </header>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>选拔流程总览</h2>
            <p class="section__desc">共 {{ data.steps.length }} 个环节，按时间顺序推进</p>
          </div>
        </div>

        <FeedbackState :empty="!data.steps.length" empty-text="管理员尚未配置选拔流程节点">
          <div class="steps">
            <div
              v-for="step in data.steps"
              :key="step.id"
              class="step"
              :class="{ 'is-active': step.state === '进行中', 'is-past': step.state === '已结束' }"
            >
              <span class="step__no">{{ step.step }}</span>
              <strong class="step__title">{{ step.title }}</strong>
              <span class="step__time">
                {{ formatDateTime(step.startAt) }} 起<br />
                {{ formatDateTime(step.endAt) }} 止
              </span>
              <span class="step__badge">
                <span class="badge" :class="stateBadges[step.state] || 'badge--givenup'">{{ step.state }}</span>
              </span>
            </div>
          </div>
        </FeedbackState>
      </section>

      <section class="section">
        <div class="section__head">
          <div>
            <h2>重要通知</h2>
            <p class="section__desc">共 {{ data.notices.length }} 条公告</p>
          </div>
        </div>

        <FeedbackState :empty="!data.notices.length" empty-text="当前暂无通知，请关注后续公告">
          <div class="notice-list">
            <div v-for="notice in data.notices" :key="notice.id" class="notice">
              <span class="notice__dot"></span>
              <div class="notice__main">
                <div class="notice__title">
                  {{ notice.title }}
                  <span class="badge" :class="levelBadges[notice.level] || 'badge--givenup'">{{ notice.level }}</span>
                </div>
                <p class="notice__text">{{ notice.content }}</p>
              </div>
              <span class="notice__time">{{ formatDateTime(notice.publishedAt) }}</span>
            </div>
          </div>
        </FeedbackState>
      </section>

      <section class="section">
        <div class="section__head">
          <h2>实训室速览</h2>
          <router-link to="/rooms" class="btn btn--ghost btn--sm">查看实训室介绍</router-link>
        </div>

        <FeedbackState :empty="!data.rooms.length" empty-text="暂无可展示的实训室">
          <div class="grid grid--4">
            <router-link v-for="room in data.rooms" :key="room.id" to="/rooms" class="room-card">
              <div class="room-card__top">
                <div>
                  <div class="room-card__code">{{ room.code }}</div>
                  <h3>{{ room.name }}</h3>
                </div>
                <span
                  class="badge"
                  :class="room.remaining > 0 ? 'badge--admitted' : 'badge--rejected'"
                >
                  {{ room.remaining > 0 ? `剩余 ${room.remaining}` : '已招满' }}
                </span>
              </div>
              <span class="room-card__direction">{{ room.direction }}</span>
              <div class="room-card__meta">
                <div>
                  名额上限<strong>{{ room.quota }}</strong>
                </div>
                <div>
                  剩余名额<strong>{{ room.remaining }}</strong>
                </div>
              </div>
              <div class="tag-row">
                <span v-for="tag in room.tags" :key="tag" class="tag">{{ tag }}</span>
              </div>
            </router-link>
          </div>
        </FeedbackState>
      </section>
    </FeedbackState>
  </main>
</template>

<style scoped>
.room-card {
  color: inherit;
}

.room-card:hover {
  text-decoration: none;
}
</style>
