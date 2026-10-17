<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createLink, updateLink } from '@/api/link'
import type { GroupItem, LinkItem } from '@/types'

const props = defineProps<{
  modelValue: boolean
  link: LinkItem | null
  groups: GroupItem[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})
const isEdit = computed(() => !!props.link)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  originalUrl: '',
  gid: '',
  channel: '',
  validType: 1,
  validDate: '',
  description: '',
})

const rules: FormRules = {
  originalUrl: [
    { required: true, message: '请输入长链接', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: '必须以 http:// 或 https:// 开头', trigger: 'blur' },
  ],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    formRef.value?.clearValidate()
    const link = props.link
    form.originalUrl = link?.originalUrl ?? ''
    form.gid = link?.gid ?? ''
    form.channel = link?.channel ?? ''
    form.validType = link?.validType ?? 1
    form.validDate = link?.validDate ?? ''
    form.description = link?.description ?? ''
  },
)

function submit() {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return
    }
    saving.value = true
    const payload = {
      originalUrl: form.originalUrl,
      gid: form.gid || undefined,
      channel: form.channel || undefined,
      validType: form.validType,
      validDate: form.validType === 2 ? form.validDate : null,
      description: form.description || undefined,
    }
    const action = isEdit.value
      ? updateLink({ ...payload, id: props.link!.id })
      : createLink(payload)

    action
      .then(() => {
        ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
        visible.value = false
        emit('saved')
      })
      .catch((e) => console.error('save link failed', e))
      .finally(() => {
        saving.value = false
      })
  })
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑短链' : '新建短链'"
    width="560px"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="原始链接" prop="originalUrl">
        <el-input v-model="form.originalUrl" placeholder="https://example.com/landing" />
      </el-form-item>
      <el-form-item label="分组">
        <el-select v-model="form.gid" placeholder="默认分组" clearable class="w-full">
          <el-option v-for="g in groups" :key="g.gid" :label="g.name" :value="g.gid" />
        </el-select>
      </el-form-item>
      <el-form-item label="渠道">
        <el-input v-model="form.channel" maxlength="32" placeholder="如 email / wechat / douyin（可空）" />
      </el-form-item>
      <el-form-item label="有效期">
        <el-radio-group v-model="form.validType">
          <el-radio :value="1">永久</el-radio>
          <el-radio :value="2">自定义</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.validType === 2" label="过期时间">
        <el-date-picker
          v-model="form.validDate"
          type="datetime"
          placeholder="选择过期时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          class="w-full"
        />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
