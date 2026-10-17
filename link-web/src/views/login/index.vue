<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Link2 } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'tenantadmin', password: '123456' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function submit() {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return
    }
    loading.value = true
    auth
      .login(form.username, form.password)
      .then(() => {
        ElMessage.success('登录成功')
        router.replace((route.query.redirect as string) || '/link')
      })
      .catch((e) => console.error('login failed', e))
      .finally(() => {
        loading.value = false
      })
  })
}

function fill(username: string) {
  form.username = username
  form.password = '123456'
}
</script>

<template>
  <div class="h-full flex items-center justify-center bg-gradient-to-br from-[#1f2d3d] via-[#2b3a4d] to-[#304156] relative overflow-hidden">
    <!-- 背景光斑：克制的品牌色氛围，不抢内容 -->
    <div class="absolute -top-32 -left-24 w-96 h-96 rounded-full bg-brand/20 blur-3xl" />
    <div class="absolute -bottom-40 -right-24 w-[28rem] h-[28rem] rounded-full bg-brand/10 blur-3xl" />

    <div class="relative w-[420px] bg-white rounded-xl shadow-2xl p-8">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-brand flex items-center justify-center text-white shrink-0">
          <Link2 :size="22" />
        </div>
        <div>
          <h1 class="text-xl font-semibold text-[#303133]">Link 短链平台</h1>
          <p class="text-xs text-[#909399] mt-0.5">多租户营销短链 · 管理后台</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="mt-7" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" size="large" class="w-full !mt-2" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="mt-6 pt-4 border-t border-[#EBEEF5] text-xs text-[#909399]">
        <p class="text-[#606266] mb-2">演示账号（密码均为 123456）</p>
        <div class="flex flex-wrap gap-2">
          <button
            class="px-2 py-1 rounded bg-page hover:bg-brand-light hover:text-brand-dark transition-colors cursor-pointer"
            @click="fill('superadmin')"
          >
            平台管理员 superadmin
          </button>
          <button
            class="px-2 py-1 rounded bg-page hover:bg-brand-light hover:text-brand-dark transition-colors cursor-pointer"
            @click="fill('tenantadmin')"
          >
            租户管理员 tenantadmin
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
