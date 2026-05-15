<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)

const formState = reactive({
  username: 'superadmin',
  password: 'superadmin123',
})

async function submit() {
  loading.value = true
  try {
    await authStore.login(formState.username, formState.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect?.toString() || '/dashboard'
    router.replace(redirect)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <div class="login-mark">AT</div>
        <div>
          <div class="login-title">自动化测试平台</div>
          <div class="login-subtitle">登录后进入你有权限访问的工作空间</div>
        </div>
      </div>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号或邮箱">
          <el-input v-model="formState.username" placeholder="请输入账号或邮箱">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="formState.password" type="password" show-password placeholder="请输入密码">
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="login-tip">默认管理员账号：superadmin / superadmin123</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: linear-gradient(180deg, #f5f7fb 0%, #eef3ff 100%);
}

.login-panel {
  width: min(420px, 100%);
  padding: 32px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.login-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #111827;
  color: #fff;
  font-weight: 700;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.login-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.login-submit {
  width: 100%;
  margin-top: 8px;
}

.login-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #6b7280;
}
</style>
