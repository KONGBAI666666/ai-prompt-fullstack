// Prompt 收藏/复制公共逻辑：列表项（HomeView）与详情对象（PromptDetailView）共用
import { ElMessage } from 'element-plus'
import { addFavorite, cancelFavorite } from '@/api/favorite'
import { recordHistory } from '@/api/history'

/**
 * @param item PromptVO 响应式对象，收藏状态与计数直接在其上更新
 */
export function usePromptActions() {
  async function toggleFavorite(item) {
    if (item.favorited) {
      await cancelFavorite(item.id)
      item.favorited = false
      item.favoriteCount--
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(item.id)
      item.favorited = true
      item.favoriteCount++
      ElMessage.success('收藏成功')
    }
  }

  async function copyPrompt(item) {
    try {
      await navigator.clipboard.writeText(item.content)
    } catch {
      ElMessage.error('复制失败，请手动复制')
      return
    }
    ElMessage.success('已复制到剪贴板')
    // 使用记录失败不影响复制体验，静默处理
    recordHistory(item.id).catch(() => {})
  }

  return { toggleFavorite, copyPrompt }
}
