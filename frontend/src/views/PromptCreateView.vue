<script setup>
// Prompt 新增页：CRUD 中的 C
// 链路：本页面 → api/prompt.js createPrompt → POST /api/prompt → PromptController.create
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPrompt } from '@/api/prompt'
import { getCategoryList } from '@/api/category'

const router = useRouter()
const formRef = ref() // el-form 组件实例，用来触发校验
const submitting = ref(false)
const categories = ref([])

const form = ref({
  title: '',
  categoryId: null,
  description: '',
  content: '',
})

// 前端校验规则：与后端 PromptDTO 的注解保持一致（前端提示快，后端兜底）
const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题长度不能超过100', trigger: 'blur' },
  ],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  description: [{ max: 500, message: '描述长度不能超过500', trigger: 'blur' }],
  content: [{ required: true, message: '请输入Prompt内容', trigger: 'blur' }],
}

onMounted(async () => {
  categories.value = await getCategoryList()
})

async function handleSubmit() {
  // validate()：校验不通过会 reject，直接中断后面的提交
  await formRef.value.validate()
  submitting.value = true
  try {
    await createPrompt(form.value)
    ElMessage.success('发布成功')
    router.push('/')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card>
    <template #header>
      <span class="page-title">✍️ 发布 Prompt</span>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="一句话概括这个 Prompt 的用途" maxlength="100" show-word-limit />
      </el-form-item>

      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 240px">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="补充说明适用场景（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="10"
          placeholder="粘贴完整的 Prompt 正文，支持多行"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">发 布</el-button>
        <el-button @click="router.back()">取 消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.page-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
