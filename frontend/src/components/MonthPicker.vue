<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const yearValue = ref('')
const monthValue = ref('')

watch(
  () => props.modelValue,
  (value) => {
    yearValue.value = value?.split('.')[0] ?? ''
    monthValue.value = value?.split('.')[1] ?? ''
  },
  { immediate: true }
)

const years = computed(() => {
  const current = new Date().getFullYear()
  const values = Array.from({ length: 50 }, (_, index) => String(current + 5 - index))
  if (yearValue.value && !values.includes(yearValue.value)) {
    values.push(yearValue.value)
  }
  return values
})

const months = Array.from({ length: 12 }, (_, index) => String(index + 1).padStart(2, '0'))

watch([yearValue, monthValue], () => {
  emit(
    'update:modelValue',
    yearValue.value && monthValue.value ? `${yearValue.value}.${monthValue.value}` : ''
  )
})
</script>

<template>
  <div class="month-picker">
    <el-select v-model="yearValue" filterable placeholder="年份" class="year-select">
      <el-option v-for="item in years" :key="item" :label="item" :value="item" />
    </el-select>
    <span class="dot">.</span>
    <el-select v-model="monthValue" placeholder="月" class="month-select">
      <el-option v-for="item in months" :key="item" :label="item" :value="item" />
    </el-select>
  </div>
</template>

<style scoped>
.month-picker {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.year-select {
  flex: 1.15;
  min-width: 0;
}

.month-select {
  flex: 0.85;
  min-width: 0;
}

.dot {
  color: #9aa7b5;
  font-weight: 700;
}
</style>
