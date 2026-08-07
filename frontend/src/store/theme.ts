import { defineStore } from 'pinia'

const THEME_KEY = 'social_theme'

/**
 * 明暗主题。写入 <html data-theme>，配合 style.css 中的 CSS 变量切换。
 * Vant 组件主题由 App.vue 的 van-config-provider 同步控制。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    isDark: localStorage.getItem(THEME_KEY) === 'dark',
  }),

  actions: {
    init() {
      document.documentElement.setAttribute('data-theme', this.isDark ? 'dark' : 'light')
    },

    toggle() {
      this.isDark = !this.isDark
      localStorage.setItem(THEME_KEY, this.isDark ? 'dark' : 'light')
      this.init()
    },

    setDark(dark: boolean) {
      this.isDark = dark
      localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
      this.init()
    },
  },
})
