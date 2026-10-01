<template>
  <el-result icon="warning" title="404" sub-title="Không tìm thấy trang bạn yêu cầu">
    <template #extra>
      <el-button type="primary" @click="$router.push('/')">Về trang chủ</el-button>
    </template>
  </el-result>
</template>
