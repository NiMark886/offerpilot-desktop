<script setup lang="ts">
defineProps<{
  suffix?: string
}>()
</script>

<template>
  <div class="brand-block">
    <div class="brand">OfferPilot<span v-if="suffix"> · {{ suffix }}</span></div>
    <p class="brand-note">一切为 AI 输出结果，不同模型可能有不同的结果，仅供参考</p>
  </div>
</template>

<style scoped>
.brand-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  line-height: 1.2;
  text-align: center;
}

.brand {
  color: #17375e;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.brand-note {
  margin: 0;
  color: #98a4b3;
  font-size: 10px;
  line-height: 1.4;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .brand-note {
    font-size: 9px;
  }
}
</style>
