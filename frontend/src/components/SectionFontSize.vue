<script setup lang="ts">
defineProps<{
  modelValue: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const sizes = [8, 8.5, 9, 9.5, 10, 10.5, 11, 11.5, 12, 12.5, 13, 14]

function update(value: number) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-select
    :model-value="modelValue"
    size="small"
    class="section-font-size"
    @update:model-value="update"
  >
    <el-option label="跟随正文" :value="0" />
    <el-option v-for="size in sizes" :key="size" :label="`${size} pt`" :value="size" />
  </el-select>
</template>

<style scoped>
.section-font-size {
  width: 112px;
}
</style>
