<script setup>
const props = defineProps({
  status: { type: String, required: true }
})

const map = {
  待面试: { cls: 'badge--pending', label: '待面试' },
  面试完成: { cls: 'badge--scored', label: '面试完成' },
  拟录取: { cls: 'badge--admitted', label: '拟录取' },
  递补: { cls: 'badge--waitlist', label: '递补' },
  已确认: { cls: 'badge--confirmed', label: '已确认' },
  已放弃: { cls: 'badge--givenup', label: '已放弃' },
  已录取: { cls: 'badge--enrolled', label: '已录取' },
  未录取: { cls: 'badge--rejected', label: '未录取' },
  公示中: { cls: 'badge--public', label: '公示中' },
  考核中: { cls: 'badge--scored', label: '考核中' },
  未填报: { cls: 'badge--givenup', label: '未填报' }
}

const conf = map[props.status] || { cls: 'badge--givenup', label: props.status }
</script>

<template>
  <span class="badge" :class="conf.cls">{{ conf.label }}</span>
</template>
