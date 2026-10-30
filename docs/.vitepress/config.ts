import { defineConfig } from 'vitepress'

// GitHub Pages serves this under /LogSleuth/. Override with DOCS_BASE for a
// custom domain (e.g. DOCS_BASE=/ npm run build).
const base = process.env.DOCS_BASE || '/LogSleuth/'

export default defineConfig({
  title: 'LogSleuth',
  description:
    'Root-free Android logcat viewer and embeddable zero-permission logging SDK',
  base,
  lastUpdated: true,
  cleanUrls: true,
  ignoreDeadLinks: false,
  head: [
    ['meta', { name: 'theme-color', content: '#2f6f4e' }]
  ],
  themeConfig: {
    siteTitle: 'LogSleuth',
    nav: [
      { text: 'Guide', link: '/guide/', activeMatch: '/guide/' },
      { text: 'Developer', link: '/developer/', activeMatch: '/developer/' },
      { text: 'GitHub', link: 'https://github.com/shiaho777/LogSleuth' },
      { text: 'Releases', link: 'https://github.com/shiaho777/LogSleuth/releases/latest' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Guide',
          items: [
            { text: 'Getting started', link: '/guide/' },
            { text: 'Log access', link: '/guide/log-access' },
            { text: 'Filters & rules', link: '/guide/filters' },
            { text: 'Recording & crashes', link: '/guide/recording-and-crashes' }
          ]
        }
      ],
      '/developer/': [
        {
          text: 'Developer',
          items: [
            { text: 'Overview', link: '/developer/' },
            { text: 'Architecture', link: '/developer/architecture' },
            { text: 'Change recipes', link: '/developer/recipes' },
            { text: 'Releasing', link: '/developer/release' }
          ]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/shiaho777/LogSleuth' },
      { icon: 'x', link: 'https://github.com/shiaho777' }
    ],
    search: { provider: 'local' },
    outline: { level: [2, 3], label: 'On this page' },
    docFooter: { prev: 'Previous', next: 'Next' },
    lastUpdated: { text: 'Last updated' },
    darkModeSwitchLabel: 'Theme',
    lightModeSwitchTitle: 'Switch to light mode',
    darkModeSwitchTitle: 'Switch to dark mode',
    sidebarMenuLabel: 'Menu',
    returnToTopLabel: 'Back to top',
    langMenuLabel: 'Language',
    externalLinkIcon: true,
    footer: {
      message:
        'Released under the Apache 2.0 license. Source, issues and releases live on GitHub.',
      copyright: 'Copyright © LogSleuth'
    }
  },
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: [
          { text: 'Guide', link: '/guide/', activeMatch: '/guide/' },
          { text: 'Developer', link: '/developer/', activeMatch: '/developer/' },
          { text: 'GitHub', link: 'https://github.com/shiaho777/LogSleuth' },
          {
            text: 'Releases',
            link: 'https://github.com/shiaho777/LogSleuth/releases/latest'
          }
        ]
      }
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      themeConfig: {
        nav: [
          { text: '指南', link: '/zh/guide/', activeMatch: '/zh/guide/' },
          { text: '开发者', link: '/developer/', activeMatch: '/developer/' },
          { text: 'GitHub', link: 'https://github.com/shiaho777/LogSleuth' },
          {
            text: '下载',
            link: 'https://github.com/shiaho777/LogSleuth/releases/latest'
          }
        ],
        sidebar: {
          '/zh/guide/': [
            {
              text: '指南',
              items: [
                { text: '快速开始', link: '/zh/guide/' },
                { text: '日志权限', link: '/zh/guide/log-access' },
                { text: '过滤与规则', link: '/zh/guide/filters' },
                { text: '录制与崩溃', link: '/zh/guide/recording-and-crashes' }
              ]
            }
          ]
        },
        outline: { level: [2, 3], label: '本页目录' },
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到浅色模式',
        darkModeSwitchTitle: '切换到深色模式',
        returnToTopLabel: '回到顶部',
        langMenuLabel: '语言'
      }
    }
  }
})
