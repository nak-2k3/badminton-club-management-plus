import { onBeforeUnmount, ref } from 'vue'

// Theo dõi kích thước màn hình để đổi bố cục (thu gọn menu, số cột...)
export function useBreakpoint(maxWidth = 768) {
  const query = window.matchMedia(`(max-width: ${maxWidth}px)`)
  const isSmall = ref(query.matches)
  const update = (e) => (isSmall.value = e.matches)

  query.addEventListener('change', update)
  onBeforeUnmount(() => query.removeEventListener('change', update))

  return { isSmall }
}
