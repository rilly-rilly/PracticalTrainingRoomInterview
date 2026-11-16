/**
 * 拼音工具：基于 pinyin-pro，覆盖全部汉字
 * 提供首字母提取、模糊搜索、姓名首字母排序
 */
import { pinyin } from 'pinyin-pro'

/**
 * 获取字符串的拼音首字母串（大写）
 * 例：张一鸣 -> ZYM ；王雨桐 -> WYT
 */
export function getInitials(str) {
  if (!str) return ''
  const result = pinyin(String(str), {
    pattern: 'first',
    toneType: 'none',
    type: 'array',
    nonZh: 'removed'
  })
  return result.join('').toUpperCase()
}

/**
 * 获取字符串的完整拼音（无声调，小写，无空格）
 * 例：张一鸣 -> zhangyiming
 */
export function getFullPinyin(str) {
  if (!str) return ''
  return pinyin(String(str), {
    toneType: 'none',
    type: 'array',
    nonZh: 'removed'
  }).join('').toLowerCase()
}

/**
 * 获取字符串第一个字符的拼音首字母（用于姓名 A-Z 分组排序）
 */
export function getFirstInitial(str) {
  if (!str) return '#'
  const first = String(str).charAt(0)
  // ASCII 字母 / 数字直接返回
  if (/[a-zA-Z]/.test(first)) return first.toUpperCase()
  if (/[0-9]/.test(first)) return '#'
  const initial = pinyin(first, { pattern: 'first', toneType: 'none' })
  return initial ? initial.toUpperCase() : '#'
}

/**
 * 模糊搜索：姓名 / 学号 / 拼音（全拼或首字母）
 * @param {string} target 目标字符串
 * @param {string} keyword 搜索关键词
 * @returns {boolean}
 */
export function matchKeyword(target, keyword) {
  if (!keyword) return true
  if (!target) return false
  const kw = String(keyword).trim().toLowerCase()
  if (!kw) return true
  const tgt = String(target).toLowerCase()

  // 1) 直接子串匹配（覆盖中文包含、学号包含、英文包含）
  if (tgt.includes(kw)) return true

  // 2) 拼音首字母串匹配（如 "zym" 匹配 "张一鸣"）
  const initials = getInitials(target).toLowerCase()
  if (initials && initials.includes(kw)) return true

  // 3) 全拼匹配（如 "zhangyiming" 匹配 "张一鸣"）
  const full = getFullPinyin(target)
  if (full && full.includes(kw)) return true

  return false
}

/**
 * 姓名首字母排序比较器
 * @param {object} a 行 A
 * @param {object} b 行 B
 * @param {function} getKey 从行取字段
 * @param {string} order 'asc' | 'desc'
 */
export function compareByInitial(a, b, getKey = (x) => x, order = 'asc') {
  const ka = String(getKey(a) || '')
  const kb = String(getKey(b) || '')
  const dir = order === 'asc' ? 1 : -1

  // 主键：拼音首字母串（如 "ZYM"）
  const ia = getInitials(ka)
  const ib = getInitials(kb)
  if (ia !== ib) return ia.localeCompare(ib) * dir

  // 次键：完整拼音（如 "zhangyiming"）
  const fa = getFullPinyin(ka)
  const fb = getFullPinyin(kb)
  if (fa !== fb) return fa.localeCompare(fb) * dir

  // 兜底：中文 locale
  return ka.localeCompare(kb, 'zh-Hans-CN') * dir
}

export default { getInitials, getFullPinyin, getFirstInitial, matchKeyword, compareByInitial }
