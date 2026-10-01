<script setup>
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABELS, GENDER_LABELS } from '@/utils/labels'

const auth = useAuthStore()
</script>

<template>
  <el-card shadow="never">
    <template #header>Xin chào, {{ auth.user?.fullName }} 👋</template>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="Email">{{ auth.user?.email }}</el-descriptions-item>
      <el-descriptions-item label="Giới tính">{{ GENDER_LABELS[auth.user?.gender] }}</el-descriptions-item>
      <el-descriptions-item label="Vai trò">{{ ROLE_LABELS[auth.role] ?? auth.role }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
