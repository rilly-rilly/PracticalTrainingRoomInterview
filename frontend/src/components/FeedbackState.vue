<script setup>
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  loadingText: { type: String, default: '正在加载数据…' },
  errorTitle: { type: String, default: '加载失败' }
})

defineEmits(['retry'])
</script>

<template>
  <div v-if="loading" class="state">
    <span class="spinner"></span>
    <strong>{{ loadingText }}</strong>
    <p>数据正在从后端接口返回（演示为本地 mock 服务）</p>
  </div>

  <div v-else-if="error" class="state state--error">
    <span class="state__icon">!</span>
    <strong>{{ errorTitle }}</strong>
    <p>{{ error.message }}</p>
    <span v-if="error.code" class="state__code">状态码 {{ error.code }}</span>
    <button class="btn btn--ghost btn--sm" @click="$emit('retry')">重新加载</button>
  </div>

  <div v-else-if="empty" class="state state--empty">
    <span class="state__icon">∅</span>
    <strong>暂无数据</strong>
    <p>{{ emptyText }}</p>
    <slot name="empty-action" />
  </div>

  <slot v-else />
</template>
