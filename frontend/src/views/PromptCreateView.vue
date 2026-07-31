<script setup>
// Prompt 新增/编辑页（双模式复用）：CRUD 中的 C 和 U
// /prompt/create → 新增模式：POST /api/prompt
// /prompt/edit/:id → 编辑模式：先加载旧数据，提交时 PUT /api/prompt/{id}
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPrompt, updatePrompt, getPromptDetail } from '@/api/prompt'
import { getCategoryList } from '@/api/category'

const route = useRoute()
const router = useRouter()
// computed：路由带 id 参数就是编辑模式
const isEdit = computed(() => !!route.params.id)
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
  // 编辑模式：加载旧数据回填表单
  if (isEdit.value) {
    const detail = await getPromptDetail(route.params.id)
    form.value = {
      title: detail.title,
      categoryId: detail.categoryId,
      description: detail.description,
      content: detail.content,
    }
  }
})

async function handleSubmit() {
  // validate()：校验不通过会 reject，直接中断后面的提交
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePrompt(route.params.id, form.value)
      ElMessage.success('修改成功')
      router.push(`/prompt/${route.params.id}`)
    } else {
      await createPrompt(form.value)
      ElMessage.success('发布成功')
      router.push('/')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card>
    <template #header>
      <span class="page-title">
        <el-icon><EditPen /></el-icon>{{ isEdit ? '编辑 Prompt' : '发布 Prompt' }}
      </span>
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
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保 存' : '发 布' }}
        </el-button>
        <el-button @click="router.back()">取 消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.page-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--app-title-font);
  font-size: 16px;
  font-weight: bold;
  color: var(--app-text-primary);
}
</style>
