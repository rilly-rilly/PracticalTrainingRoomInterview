<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="page">
    <div class="card forbidden-card">
      <div class="card__body">
        <div class="state forbidden-state">
          <span class="state__icon forbidden-code">403</span>
          <strong>无权限访问</strong>
          <p>请求已被后端角色校验拦截：当前登录身份不具备访问该资源的权限。</p>
        </div>

        <div class="forbidden-section">
          <h3>典型触发场景</h3>
          <div class="stack">
            <div class="hint hint--box">学生账号访问导师 / 管理员接口（如面试评分、录取统筹、导入账号），后端按角色直接返回 403。</div>
            <div class="hint hint--box">导师账号访问管理员接口（如调整名额、发布公告、结果公示），同样会被角色校验拦截。</div>
            <div class="hint hint--box">学生尝试修改或确认他人的意向 ID，归属校验失败并返回 403。</div>
          </div>
        </div>

        <div class="btn-row forbidden-actions">
          <button type="button" class="btn" @click="router.push('/')">返回首页</button>
          <button type="button" class="btn btn--ghost" @click="router.push('/login')">切换账号登录</button>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.forbidden-card {
  max-width: 760px;
  margin: 24px auto;
}

.forbidden-state {
  border: none;
  background: transparent;
  padding: 8px 0 4px;
}

.forbidden-code {
  width: 76px;
  height: 76px;
  font-size: 30px;
  font-weight: 800;
  color: var(--danger);
  background: var(--danger-soft);
  border-color: transparent;
}

.forbidden-section {
  margin-top: 22px;
}

.forbidden-section h3 {
  margin-bottom: 12px;
}

.forbidden-actions {
  margin-top: 24px;
}
</style>
