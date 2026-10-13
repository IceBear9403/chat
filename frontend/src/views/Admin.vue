<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { adminApi } from '@/api/admin'
import type { AuditMomentVO, ReportAdminVO, UserAdminVO, UserLogVO } from '@/types'
import { formatTime, visibilityText } from '@/utils/format'

const router = useRouter()
const tab = ref<'users' | 'audits' | 'reports' | 'logs'>('users')

// 首次进入默认展示「用户管理」，主动加载一次。
// 不能依赖 van-list 的 @load 首屏触发：list 为空时 van-list 不挂载（v-if="length > 0"）。
onMounted(() => {
  loadUsers()
})

// ---------- 用户管理 ----------
const users = ref<UserAdminVO[]>([])
const usersLoading = ref(false)
const usersFinished = ref(false)
const usersPage = ref(1)
const keyword = ref('')
/** 状态筛选：-1 表示全部 */
const statusFilter = ref<number>(-1)

async function loadUsers(reset = false) {
  if (reset) {
    usersPage.value = 1
    users.value = []
    usersFinished.value = false
  }
  if (usersLoading.value) return
  usersLoading.value = true
  try {
    const page = await adminApi.listUsers({
      keyword: keyword.value || undefined,
      status: statusFilter.value >= 0 ? statusFilter.value : undefined,
      page: usersPage.value,
      size: 10,
    })
    users.value.push(...page.records)
    usersPage.value += 1
    usersFinished.value = !page.hasMore
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    usersLoading.value = false
  }
}

async function onBanToggle(user: UserAdminVO) {
  if (user.isAdmin) {
    showToast('不能操作管理员账号')
    return
  }
  const banning = user.status === 0
  try {
    await showConfirmDialog({
      title: banning ? '封禁用户' : '解封用户',
      message: banning ? `确定封禁「${user.nickname || user.phone || user.email}」？其所有会话将立即失效。` : `确定解封「${user.nickname || user.phone || user.email}」？`,
    })
  } catch {
    return
  }
  try {
    await adminApi.setUserStatus(user.id, banning ? 1 : 0)
    user.status = banning ? 1 : 0
    showToast(banning ? '已封禁' : '已解封')
  } catch (e) {
    showToast((e as Error).message)
  }
}

function onSearch() {
  loadUsers(true)
}

// ---------- 改昵称（管理员不受昵称修改间隔限制） ----------
const nicknameDialog = ref(false)
const nicknameTarget = ref<UserAdminVO | null>(null)
const nicknameValue = ref('')
const nicknameSaving = ref(false)

function onEditNickname(u: UserAdminVO) {
  nicknameTarget.value = u
  nicknameValue.value = u.nickname || ''
  nicknameDialog.value = true
}

/** 保存昵称；成功返回 true（关闭弹窗），失败/为空返回 false（保持打开）。 */
async function doNicknameSave(): Promise<boolean> {
  const target = nicknameTarget.value
  const value = nicknameValue.value.trim()
  if (!target) return true
  if (!value) {
    showToast('昵称不能为空')
    return false
  }
  nicknameSaving.value = true
  try {
    await adminApi.setUserNickname(target.id, value)
    target.nickname = value
    showToast('已修改昵称')
    return true
  } catch (e) {
    showToast((e as Error).message)
    return false
  } finally {
    nicknameSaving.value = false
  }
}

function onNicknameBeforeClose(action: string): boolean | Promise<boolean> {
  if (action !== 'confirm') return true
  return doNicknameSave()
}

// ---------- 内容审核 ----------
const audits = ref<AuditMomentVO[]>([])
const auditsLoading = ref(false)
const auditsFinished = ref(false)
const auditsPage = ref(1)

async function loadAudits(reset = false) {
  if (reset) {
    auditsPage.value = 1
    audits.value = []
    auditsFinished.value = false
  }
  if (auditsLoading.value) return
  auditsLoading.value = true
  try {
    const page = await adminApi.listAudits({ page: auditsPage.value, size: 10 })
    audits.value.push(...page.records)
    auditsPage.value += 1
    auditsFinished.value = !page.hasMore
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    auditsLoading.value = false
  }
}

async function onAudit(m: AuditMomentVO, action: 'approve' | 'reject') {
  try {
    await adminApi.auditMoment(m.id, action)
    audits.value = audits.value.filter((x) => x.id !== m.id)
    showToast(action === 'approve' ? '已放行' : '已拒绝')
  } catch (e) {
    showToast((e as Error).message)
  }
}

// ---------- 举报处理 ----------
const reports = ref<ReportAdminVO[]>([])
const reportsLoading = ref(false)
const reportsFinished = ref(false)
const reportsPage = ref(1)

async function loadReports(reset = false) {
  if (reset) {
    reportsPage.value = 1
    reports.value = []
    reportsFinished.value = false
  }
  if (reportsLoading.value) return
  reportsLoading.value = true
  try {
    const page = await adminApi.listReports({ page: reportsPage.value, size: 10 })
    reports.value.push(...page.records)
    reportsPage.value += 1
    reportsFinished.value = !page.hasMore
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    reportsLoading.value = false
  }
}

async function onHandleReport(r: ReportAdminVO, action: 'delete' | 'ignore') {
  if (action === 'delete') {
    try {
      await showConfirmDialog({
        title: '删除动态',
        message: '确定删除该被举报动态吗？（逻辑删除，可追查）',
      })
    } catch {
      return
    }
  }
  try {
    await adminApi.handleReport(r.id, action)
    reports.value = reports.value.filter((x) => x.id !== r.id)
    showToast(action === 'delete' ? '已删除动态' : '已忽略')
  } catch (e) {
    showToast((e as Error).message)
  }
}

// ---------- 操作日志 ----------
const logs = ref<UserLogVO[]>([])
const logsLoading = ref(false)
const logsFinished = ref(false)
const logsPage = ref(1)

async function loadLogs(reset = false) {
  if (reset) {
    logsPage.value = 1
    logs.value = []
    logsFinished.value = false
  }
  if (logsLoading.value) return
  logsLoading.value = true
  try {
    const page = await adminApi.listLogs({ page: logsPage.value, size: 20 })
    logs.value.push(...page.records)
    logsPage.value += 1
    logsFinished.value = !page.hasMore
  } catch (e) {
    showToast((e as Error).message)
  } finally {
    logsLoading.value = false
  }
}

function onTabChange(name: string | number) {
  tab.value = name as typeof tab.value
  // 切到新页签时回顶，让新页签内容直接聚焦可见（sticky 页签会保持滚动位置）
  window.scrollTo(0, 0)
  if (tab.value === 'users' && users.value.length === 0) loadUsers(true)
  else if (tab.value === 'audits' && audits.value.length === 0) loadAudits(true)
  else if (tab.value === 'reports' && reports.value.length === 0) loadReports(true)
  else if (tab.value === 'logs' && logs.value.length === 0) loadLogs(true)
}

function userStatusText(s: number): string {
  return s === 0 ? '正常' : s === 1 ? '封禁' : '注销'
}

function actionText(a: string): string {
  const map: Record<string, string> = {
    register: '注册',
    login: '密码登录',
    loginCode: '验证码登录',
    logout: '退出登录',
    realname: '实名认证',
    deleteAccount: '注销账号',
    ban: '封禁用户',
    unban: '解封用户',
    audit: '审核动态',
    report: '举报',
    reportHandle: '处理举报',
  }
  return map[a] ?? a
}
</script>

<template>
  <div class="page-container admin-page">
    <div class="page-header">
      <van-icon name="arrow-left" size="20" class="back-icon" @click="router.back()" />
      <span>管理后台</span>
    </div>

    <van-tabs
      v-model:active="tab"
      sticky
      color="#1e90ff"
      title-active-color="#1e90ff"
      @change="onTabChange"
    >
      <!-- 用户管理 -->
      <van-tab title="用户管理" name="users">
        <div class="toolbar">
          <van-field
            v-model="keyword"
            placeholder="昵称 / 邮箱 / 手机号"
            clearable
            @keyup.enter="onSearch"
          >
            <template #button>
              <van-button size="small" type="primary" @click="onSearch">搜索</van-button>
            </template>
          </van-field>
          <van-dropdown-menu>
            <van-dropdown-item
              v-model="statusFilter"
              :options="[{ text: '全部状态', value: -1 }, { text: '正常', value: 0 }, { text: '封禁', value: 1 }, { text: '注销', value: 2 }]"
              @change="onSearch"
            />
          </van-dropdown-menu>
        </div>

        <div v-if="users.length === 0 && !usersLoading" class="empty">
          <van-empty description="暂无用户" />
        </div>
        <div v-else v-for="u in users" :key="u.id" class="admin-item">
          <van-image round width="38" height="38" :src="u.avatar || ''" fit="cover">
            <template #error>
              <div class="avatar-fallback">{{ u.nickname?.slice(0, 1) || 'U' }}</div>
            </template>
          </van-image>
          <div class="admin-item-body">
            <div class="admin-item-title">
              {{ u.nickname || '未设置昵称' }}
              <van-tag v-if="u.isAdmin" type="primary" plain>管理员</van-tag>
              <van-tag v-if="u.realnameStatus === 1" type="success" plain>已实名</van-tag>
            </div>
            <div class="admin-item-sub text-secondary">
              {{ u.phone || u.email || '—' }} · {{ userStatusText(u.status) }} · 注册于 {{ formatTime(u.createdAt) }}
            </div>
          </div>
          <van-button size="small" type="primary" plain @click="onEditNickname(u)">改昵称</van-button>
          <van-button
            v-if="u.status === 0"
            size="small"
            type="danger"
            plain
            @click="onBanToggle(u)"
          >封禁</van-button>
          <van-button
            v-else-if="u.status === 1"
            size="small"
            type="primary"
            plain
            @click="onBanToggle(u)"
          >解封</van-button>
        </div>
        <van-list
          v-if="users.length > 0"
          v-model:loading="usersLoading"
          :finished="usersFinished"
          finished-text="没有更多了"
          @load="loadUsers()"
        />
      </van-tab>

      <!-- 内容审核 -->
      <van-tab title="内容审核" name="audits">
        <div v-if="audits.length === 0 && !auditsLoading" class="empty">
          <van-empty description="没有待审核动态" />
        </div>
        <div v-else v-for="m in audits" :key="m.id" class="admin-item audit-item">
          <div class="admin-item-body">
            <div class="admin-item-title">
              @{{ m.authorNickname || m.authorId }}
              <van-tag plain>{{ visibilityText(m.visibility) }}</van-tag>
            </div>
            <p v-if="m.content" class="audit-content">{{ m.content }}</p>
            <div v-if="m.media.length" class="audit-media">
              <img v-for="mm in m.media.slice(0, 4)" :key="mm.id" :src="mm.url" class="audit-thumb" />
            </div>
            <div class="admin-item-sub text-secondary">{{ formatTime(m.createdAt) }}</div>
            <div class="audit-actions">
              <van-button size="small" type="success" plain @click="onAudit(m, 'approve')">通过</van-button>
              <van-button size="small" type="danger" plain @click="onAudit(m, 'reject')">不通过</van-button>
            </div>
          </div>
        </div>
        <van-list
          v-if="audits.length > 0"
          v-model:loading="auditsLoading"
          :finished="auditsFinished"
          finished-text="没有更多了"
          @load="loadAudits()"
        />
      </van-tab>

      <!-- 举报处理 -->
      <van-tab title="举报处理" name="reports">
        <div v-if="reports.length === 0 && !reportsLoading" class="empty">
          <van-empty description="没有待处理的举报" />
        </div>
        <div v-else v-for="r in reports" :key="r.id" class="admin-item">
          <div class="admin-item-body">
            <div class="admin-item-title">举报动态 #{{ r.momentId }}</div>
            <p v-if="r.momentContent" class="audit-content">被举报内容：{{ r.momentContent }}</p>
            <div class="admin-item-sub text-secondary">
              作者：{{ r.momentAuthorNickname || '未知' }} · 举报人：{{ r.reporterNickname || '未知' }}
            </div>
            <div v-if="r.reason" class="admin-item-sub">理由：{{ r.reason }}</div>
            <div class="admin-item-sub text-secondary">{{ formatTime(r.createdAt) }}</div>
            <div class="audit-actions">
              <van-button size="small" type="danger" plain @click="onHandleReport(r, 'delete')">删除动态</van-button>
              <van-button size="small" type="primary" plain @click="onHandleReport(r, 'ignore')">忽略</van-button>
            </div>
          </div>
        </div>
        <van-list
          v-if="reports.length > 0"
          v-model:loading="reportsLoading"
          :finished="reportsFinished"
          finished-text="没有更多了"
          @load="loadReports()"
        />
      </van-tab>

      <!-- 操作日志 -->
      <van-tab title="操作日志" name="logs">
        <div v-if="logs.length === 0 && !logsLoading" class="empty">
          <van-empty description="暂无操作日志" />
        </div>
        <div v-else v-for="l in logs" :key="l.id" class="log-item">
          <div class="log-title">
            <van-tag plain>{{ actionText(l.action) }}</van-tag>
            <span>用户 #{{ l.userId }}</span>
          </div>
          <div class="log-detail text-secondary">
            {{ l.detail || '—' }}
          </div>
          <div class="log-meta text-secondary">
            {{ formatTime(l.createdAt) }} · IP {{ l.ip || '未知' }}
          </div>
        </div>
        <van-list
          v-if="logs.length > 0"
          v-model:loading="logsLoading"
          :finished="logsFinished"
          finished-text="没有更多了"
          @load="loadLogs()"
        />
      </van-tab>
    </van-tabs>

    <!-- 改昵称弹窗（管理员不受用户昵称修改间隔限制） -->
    <van-dialog
      v-model:show="nicknameDialog"
      title="修改昵称"
      show-cancel-button
      confirm-button-text="保存"
      :before-close="onNicknameBeforeClose"
    >
      <div class="nickname-dialog-body">
        <p class="text-secondary nickname-tip">管理员修改昵称不受用户昵称修改间隔限制。</p>
        <van-field
          v-model="nicknameValue"
          :maxlength="20"
          clearable
          placeholder="请输入新昵称"
        />
      </div>
    </van-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}

/* 选中页签的底部蓝色指示条（强制蓝色，避免主题变量未生效时显示异常） */
:deep(.van-tabs__line) {
  background-color: #1e90ff;
}

.back-icon {
  margin-right: 8px;
  cursor: pointer;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
}

.toolbar .van-field {
  flex: 1;
}

.avatar-fallback {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4da8ff, #1e90ff);
  color: #fff;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.admin-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 12px;
  padding: 12px;
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: var(--shadow-card);
}

.admin-item-body {
  flex: 1;
  min-width: 0;
}

.admin-item-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.admin-item-sub {
  font-size: 12px;
  margin-top: 4px;
}

.audit-content {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-media {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.audit-thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
}

.audit-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.log-item {
  margin: 8px 12px;
  padding: 12px;
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: var(--shadow-card);
}

.log-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.log-detail {
  margin-top: 4px;
  font-size: 13px;
}

.log-meta {
  margin-top: 4px;
  font-size: 12px;
}

.nickname-dialog-body {
  padding: 4px 20px 24px;
}

.nickname-tip {
  margin: 0 0 12px;
  font-size: 12px;
}

.empty {
  padding-top: 32px;
}
</style>
