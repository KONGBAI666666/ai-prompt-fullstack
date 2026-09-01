// 后端 LocalDateTime 序列化为 "2026-08-25T10:30:00"，转成 "2026-08-25 10:30:00" 展示
export function formatTime(time) {
  return time ? time.replace('T', ' ').slice(0, 19) : ''
}
