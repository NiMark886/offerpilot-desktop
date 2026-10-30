<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

type Mode = 'login' | 'register'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<Mode>('login')
const loading = ref(false)
const formRef = ref<FormInstance>()

const loginForm = reactive({ account: '', password: '' })
const registerForm = reactive({ email: '', password: '', nickname: '' })

const activeForm = computed(() => (mode.value === 'login' ? loginForm : registerForm))

const loginRules: FormRules = {
  account: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }]
}

const registerRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱', trigger: 'blur' }
  ],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const activeRules = computed<FormRules>(() => (mode.value === 'login' ? loginRules : registerRules))

function switchMode(next: Mode) {
  mode.value = next
  formRef.value?.clearValidate()
}

async function submit() {
  const form = formRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(loginForm.account, loginForm.password)
    } else {
      await auth.register(registerForm.email, registerForm.password, registerForm.nickname)
    }
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="brand-panel">
      <div class="brand-mark">OfferPilot</div>
      <p class="brand-note">一切为 AI 输出结果，不同模型可能有不同的结果，仅供参考</p>
      <h1>把一次真实的技术面试，<br />装进桌面。</h1>
      <p>选岗位 · 传简历 · 多轮追问 · 五维评分 · 薄弱点复练</p>
      <div class="brand-points">
        <span>Java 后端</span>
        <span>Java + AI</span>
        <span>Spring Cloud</span>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <div class="mode-switch">
          <button
            :class="{ active: mode === 'login' }"
            type="button"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            :class="{ active: mode === 'register' }"
            type="button"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>

        <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
        <p class="hint">
          {{ mode === 'login' ? '登录后继续你的面试训练。' : '注册后即可开始第一场模拟面试。' }}
        </p>

        <el-form
          ref="formRef"
          :model="activeForm"
          :rules="activeRules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <template v-if="mode === 'login'">
            <el-form-item label="邮箱" prop="account">
              <el-input v-model="loginForm.account" placeholder="you@example.com" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                @keyup.enter="submit"
              />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="怎么称呼你" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="registerForm.email" placeholder="you@example.com" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                @keyup.enter="submit"
              />
            </el-form-item>
          </template>

          <el-button
            class="submit-button"
            type="primary"
            :loading="loading"
            native-type="submit"
          >
            {{ mode === 'login' ? '登录' : '注册并开始' }}
          </el-button>
        </el-form>

        <p class="switch-line">
          {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
          <button type="button" @click="switchMode(mode === 'login' ? 'register' : 'login')">
            {{ mode === 'login' ? '立即注册' : '去登录' }}
          </button>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: #f5f7fa;
}

.brand-panel {
  position: relative;
  padding: 72px 64px;
  color: #fff;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.16), transparent 34%),
    linear-gradient(140deg, #17375e 0%, #1f4e79 55%, #2e75b6 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -120px;
  bottom: -160px;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.brand-mark {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.08em;
  opacity: 0.9;
  margin-bottom: 8px;
}

.brand-panel .brand-note {
  margin: 0 0 28px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
  line-height: 1.6;
}

.brand-panel h1 {
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.2;
  margin: 0 0 20px;
  font-weight: 700;
}

.brand-panel p {
  font-size: 17px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.78);
  margin: 0;
}

.brand-points {
  display: flex;
  gap: 10px;
  margin-top: 36px;
  flex-wrap: wrap;
}

.brand-points span {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
}

.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e4ecf5;
  border-radius: 20px;
  box-shadow: 0 18px 50px rgba(31, 78, 121, 0.1);
  backdrop-filter: blur(10px);
}

.mode-switch {
  display: inline-flex;
  padding: 4px;
  background: #eef4fb;
  border-radius: 10px;
  margin-bottom: 28px;
}

.mode-switch button {
  border: 0;
  background: transparent;
  padding: 8px 22px;
  border-radius: 8px;
  cursor: pointer;
  color: #5b6b7f;
  font-size: 14px;
}

.mode-switch button.active {
  background: #fff;
  color: #1f4e79;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(31, 78, 121, 0.12);
}

.form-card h2 {
  margin: 0 0 8px;
  color: #17375e;
  font-size: 26px;
}

.hint {
  margin: 0 0 28px;
  color: #7f8c9d;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  font-size: 15px;
  border-radius: 10px;
}

.switch-line {
  margin: 22px 0 0;
  text-align: center;
  color: #7f8c9d;
  font-size: 13px;
}

.switch-line button {
  border: 0;
  background: transparent;
  color: #2e75b6;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}

@media (max-width: 860px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding: 48px 28px;
  }

  .brand-panel h1 {
    font-size: 32px;
  }

  .form-panel {
    padding: 28px 18px 48px;
  }

  .form-card {
    padding: 28px 22px;
  }
}
</style>
