<script setup>
import { toasts, removeToast } from '../utils/toast'
</script>

<template>
  <div class="toast-host">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="toast"
      :class="`toast--${t.type}`"
      @click="removeToast(t.id)"
    >
      <span v-if="t.code" class="toast__code">{{ t.code }}</span>
      <span>{{ t.message }}</span>
    </div>
  </div>
</template>
