import { usePermission } from '../../hooks/usePermission.js'

/**
 * Component kiểm soát hiển thị UI theo Permission.
 * - permission: Kiểm tra 1 quyền duy nhất
 * - anyOf: Mảng quyền (chỉ cần thỏa mãn ít nhất 1 quyền - OR)
 * - allOf: Mảng quyền (phải thỏa mãn tất cả quyền - AND)
 * - not: Đảo ngược kết quả kiểm tra
 * - fallback: Phần tử hiển thị thay thế khi không đủ quyền (mặc định: null)
 */
export function Can({
  permission = null,
  anyOf = null,
  allOf = null,
  not = false,
  fallback = null,
  children,
}) {
  const { can, canAny, canAll } = usePermission()

  let allowed = true
  if (permission) {
    allowed = allowed && can(permission)
  }
  if (Array.isArray(anyOf) && anyOf.length > 0) {
    allowed = allowed && canAny(anyOf)
  }
  if (Array.isArray(allOf) && allOf.length > 0) {
    allowed = allowed && canAll(allOf)
  }

  const finalAllowed = not ? !allowed : allowed

  if (!finalAllowed) {
    return fallback
  }

  return typeof children === 'function' ? children({ allowed: finalAllowed }) : children
}

export default Can
