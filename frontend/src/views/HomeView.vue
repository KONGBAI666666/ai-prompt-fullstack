<script setup>
// 首页 = Prompt 列表页：搜索 + 分类筛选 + 分页 + 收藏 + 复制使用
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPromptList } from '@/api/prompt'
import { getCategoryList } from '@/api/category'
import { usePromptActions } from '@/composables/usePromptActions'

// ---------- 列表状态 ----------
const list = ref([]) // 当前页的 Prompt 数组（PromptVO[]）
const total = ref(0) // 总条数，分页组件需要
const loading = ref(false)

// 查询条件：和后端 /prompt/list 的参数一一对应
const query = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  categoryId: null,
})

const categories = ref([]) // 分类下拉选项
const router = useRouter()
const { toggleFavorite, copyPrompt } = usePromptActions()

// ---------- 数据加载 ----------
async function loadList() {
  loading.value = true
  try {
    // 拦截器已拆 Result，这里拿到的是 MyBatis-Plus 的 Page 对象
    const page = await getPromptList({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      // 空字符串/null 不传给后端
      keyword: query.value.keyword || undefined,
      categoryId: query.value.categoryId || undefined,
    })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categories.value = await getCategoryList()
}

// 搜索/筛选变化时回到第一页重查
function handleSearch() {
  query.value.pageNum = 1
  loadList()
}

onMounted(() => {
  loadList()
  loadCategories()
})
</script>

<template>
  <div>
    <!-- 搜索栏 -->
    <el-card class="search-bar" shadow="never">
      <div class="search-row">
        <el-input
          v-model="query.keyword"
          placeholder="搜索标题或描述关键词"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="query.categoryId"
          placeholder="全部分类"
          clearable
          class="category-select"
          @change="handleSearch"
        >
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="primary" @click="router.push('/prompt/create')">
          <el-icon class="btn-icon"><EditPen /></el-icon>发布 Prompt
        </el-button>
      </div>
    </el-card>

    <!-- Prompt 列表 -->
    <div v-loading="loading" class="prompt-list">
      <el-empty v-if="!loading && list.length === 0" description="暂无数据，换个关键词试试" />

      <el-card v-for="item in list" :key="item.id" class="prompt-card" shadow="hover">
        <div class="card-header">
          <span class="card-title" @click="router.push(`/prompt/${item.id}`)">{{ item.title }}</span>
          <el-tag v-if="item.categoryName" size="small">{{ item.categoryName }}</el-tag>
        </div>

        <p class="card-desc">{{ item.description || '暂无描述' }}</p>
        <pre class="card-content">{{ item.content }}</pre>

        <div class="card-footer">
          <span class="meta">
            <el-icon><User /></el-icon> {{ item.username }}
            <el-icon><View /></el-icon> {{ item.viewCount }}
            <el-icon><Star /></el-icon> {{ item.favoriteCount }}
          </span>
          <span class="actions">
            <el-button
              size="small"
              :type="item.favorited ? 'warning' : 'default'"
              @click="toggleFavorite(item)"
            >
              {{ item.favorited ? '已收藏' : '收藏' }}
            </el-button>
            <el-button size="small" type="primary" @click="copyPrompt(item)">复制使用</el-button>
          </span>
        </div>
      </el-card>
    </div>

    <!-- 分页：current-page/page-size 双向绑定 query，变化时重查 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[5, 10, 20]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="handleSearch"
      />
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  margin-bottom: 16px;
}

.search-row {
  display: flex;
  gap: 12px;
}

.search-input {
  flex: 1;
}

.category-select {
  width: 180px;
}

.prompt-list {
  min-height: 200px;
}

.prompt-card {
  margin-bottom: 14px;
}

/* 暗面：悬停时细描边发光；明面：阴影上浮 */
.prompt-card:hover {
  box-shadow: var(--app-shadow-hover);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: var(--app-text-primary);
  cursor: pointer;
}

.card-title:hover {
  color: var(--app-brand);
}

.card-desc {
  color: var(--app-text-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}

/* Prompt 正文：代码块风格，保留换行，最多显示6行 */
.card-content {
  background: var(--app-code-bg);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 126px;
  overflow-y: auto;
  color: var(--app-text-secondary);
  margin-bottom: 10px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.meta .el-icon {
  margin-left: 6px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
