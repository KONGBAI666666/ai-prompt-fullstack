// 主题状态管理：明/暗切换 + localStorage 记忆
// theme 定义在模块顶层 = 单例，所有组件共享同一份状态
import { ref, computed } from 'vue'

const THEME_KEY = 'theme'
const theme = ref(localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light')

// 把主题同步到 <html> 的 class（Element Plus 暗黑模式靠 html.dark 生效）
function applyTheme() {
  document.documentElement.classList.toggle('dark', theme.value === 'dark')
}

// 模块首次加载（应用启动）时立即生效，避免刷新闪白
applyTheme()

export function useTheme() {
  const isDark = computed(() => theme.value === 'dark')

  function toggleTheme() {
    theme.value = isDark.value ? 'light' : 'dark'
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme()
  }

  return { theme, isDark, toggleTheme }
}
