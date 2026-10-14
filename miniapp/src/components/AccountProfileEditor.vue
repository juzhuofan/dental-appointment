<template>
  <view v-if="visible" class="editor-mask" :style="{ bottom: `${keyboardHeight}px` }" @click="close">
    <view class="editor-panel" role="dialog" aria-modal="true" aria-label="编辑个人资料" @click.stop>
      <view class="editor-header">
        <view>
          <view class="editor-title">编辑个人资料</view>
          <view class="editor-description">头像和昵称均为选填，随时可以修改</view>
        </view>
        <button class="icon-button close-button" aria-label="关闭资料编辑" :disabled="saving" @click="close">×</button>
      </view>
      <scroll-view scroll-y class="editor-content" :style="{ maxHeight: `${contentHeight}px` }">
        <view class="avatar-section">
          <!-- #ifdef MP-WEIXIN -->
          <button id="account-avatar-privacy-agree" class="avatar-button" :open-type="avatarOpenType"
            :disabled="saving || privacyChecking" aria-label="更换头像"
            @click="avatarTapped" @chooseavatar="handleChooseAvatar" @agreeprivacyauthorization="agreePrivacy">
            <image v-if="avatarPreview" class="account-avatar" :src="avatarPreview" mode="aspectFill" />
            <DefaultAvatar v-else :size="128" />
            <view class="camera-badge"><view class="camera-icon" /></view>
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <button class="avatar-button" :disabled="saving" aria-label="更换头像" @click="chooseLocalAvatar">
            <image v-if="avatarPreview" class="account-avatar" :src="avatarPreview" mode="aspectFill" />
            <DefaultAvatar v-else :size="128" />
            <view class="camera-badge"><view class="camera-icon" /></view>
          </button>
          <!-- #endif -->
          <view class="avatar-label">{{ avatarPath ? '已选择新头像' : '点击更换头像' }}</view>
          <view v-if="avatarPath" class="avatar-caption">点击下方保存后生效</view>
        </view>
        <view v-if="privacyChecking || privacyError || authState.privacyRequired" class="privacy-message">
          <text v-if="privacyChecking">正在准备头像选择…</text>
          <template v-else-if="privacyError"><text>{{ privacyError }}</text><button class="text-button" @click="loadPrivacy">重试</button></template>
          <template v-else><text>首次更换头像，请先同意隐私指引，再次点击头像即可选择。</text><button class="text-button" @click="openPrivacyContract">查看隐私指引</button></template>
        </view>
        <view class="nickname-field" :class="{ 'is-focused': nicknameFocused, 'is-error': nicknameError }">
          <view class="field-heading"><text class="nickname-label">昵称</text><text class="optional-label">选填</text></view>
          <view class="nickname-control">
            <!-- #ifdef MP-WEIXIN -->
            <input v-model="displayName" class="nickname-input" type="nickname" maxlength="80"
              placeholder="填写昵称或选择微信昵称" :disabled="saving" :adjust-position="false"
              :cursor-spacing="24" @input="nicknameError = ''" @focus="nicknameFocused = true"
              @blur="blurNickname" @keyboardheightchange="keyboardChanged" @confirm="confirmNickname" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <input v-model="displayName" class="nickname-input" maxlength="80"
              placeholder="填写你的昵称" :disabled="saving" @input="nicknameError = ''"
              @focus="nicknameFocused = true" @blur="blurNickname" @confirm="confirmNickname" />
            <!-- #endif -->
            <button v-if="displayName" class="icon-button clear-button" :disabled="saving" aria-label="清空昵称"
              @click="displayName = ''; nicknameError = ''">×</button>
          </view>
        </view>
        <view class="field-help" :class="{ 'field-help-error': nicknameError }">{{ nicknameError || '不填写昵称，会保留当前名称。' }}</view>
        <view class="editor-note">就诊姓名和联系电话，请在“就诊人管理”中填写。</view>
      </scroll-view>
      <view class="editor-footer" :class="{ 'keyboard-visible': keyboardHeight > 0 }">
        <view class="status-slot" aria-live="polite"><view v-if="error" class="editor-error">{{ error }}</view></view>
        <button class="save-button" :class="{ 'is-loading': saving, 'is-success': saved }"
          :disabled="saving || !hasChanges || saved" :loading="saving" @click="save">
          {{ saved ? '已保存' : saving ? '正在保存…' : '保存资料' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue';
import { api } from '../utils/request';
import { authState, hasSession, saveUser } from '../utils/session';
import { refreshPrivacyRequirement, uploadAvatar } from '../utils/auth';
import { completePrivacyAuthorization, isWechatMiniProgram, openPrivacyContract } from '../utils/wechat';
import DefaultAvatar from './DefaultAvatar.vue';

const props = defineProps({ visible: { type: Boolean, default: false } });
const emit = defineEmits(['close']);
const user = computed(() => authState.user);
const displayName = ref('');
const baselineName = ref('');
const avatarPath = ref('');
const saving = ref(false);
const saved = ref(false);
const error = ref('');
const nicknameError = ref('');
const nicknameFocused = ref(false);
const privacyChecking = ref(false);
const privacyError = ref('');
const keyboardHeight = ref(0);
const windowHeight = ref(800);
let editorGeneration = 0;
let tabBarHidden = false;

const avatarPreview = computed(() => avatarPath.value || user.value?.avatarDisplayUrl || user.value?.avatarUrl || '');
const hasChanges = computed(() => Boolean(avatarPath.value)
  || Boolean(displayName.value.trim() && displayName.value.trim() !== baselineName.value));
const avatarOpenType = computed(() => privacyChecking.value || privacyError.value ? ''
  : authState.privacyRequired ? 'agreePrivacyAuthorization' : 'chooseAvatar');
const contentHeight = computed(() => Math.max(110, windowHeight.value - keyboardHeight.value - 210));

watch(() => props.visible, visible => {
  editorGeneration++;
  keyboardHeight.value = 0;
  if (!visible) { restoreTabBar(); return; }
  if (typeof uni.hideTabBar === 'function') {
    tabBarHidden = true;
    uni.hideTabBar({ animation: false, fail() { tabBarHidden = false; } });
  }
  baselineName.value = user.value?.displayName || '微信用户';
  displayName.value = baselineName.value === '微信用户' ? '' : baselineName.value;
  avatarPath.value = '';
  error.value = '';
  nicknameError.value = '';
  nicknameFocused.value = false;
  saved.value = false;
  const windowInfo = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync?.();
  windowHeight.value = windowInfo?.windowHeight || 800;
  loadPrivacy();
}, { immediate: true });

function restoreTabBar() {
  if (!tabBarHidden) return;
  tabBarHidden = false;
  uni.showTabBar?.({ animation: false, fail() {} });
}

onUnmounted(() => { editorGeneration++; restoreTabBar(); });

async function loadPrivacy() {
  const generation = editorGeneration;
  privacyError.value = '';
  if (!isWechatMiniProgram()) { privacyChecking.value = false; return; }
  privacyChecking.value = true;
  try { await refreshPrivacyRequirement(); }
  catch { if (generation === editorGeneration) privacyError.value = '头像选择暂不可用，昵称仍可正常保存。'; }
  finally { if (generation === editorGeneration) privacyChecking.value = false; }
}

function avatarTapped() { if (privacyError.value) loadPrivacy(); }

async function agreePrivacy() {
  completePrivacyAuthorization('account-avatar-privacy-agree');
  await loadPrivacy();
}

function handleChooseAvatar(event) {
  if (saving.value || privacyChecking.value || privacyError.value || authState.privacyRequired) return;
  const path = event.detail?.avatarUrl;
  if (path) { avatarPath.value = path; error.value = ''; }
}

function chooseLocalAvatar() {
  if (saving.value) return;
  uni.chooseImage({
    count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
    success(result) {
      const path = result.tempFilePaths?.[0];
      if (path) { avatarPath.value = path; error.value = ''; }
    },
    fail(result) { if (!/cancel/i.test(result?.errMsg || '')) error.value = '未能选择图片，请再试一次。'; }
  });
}

function syncDisplayName(event) {
  if (typeof event.detail?.value === 'string') displayName.value = event.detail.value;
}

function blurNickname(event) {
  syncDisplayName(event);
  nicknameFocused.value = false;
}

function confirmNickname(event) { syncDisplayName(event); save(); }

function keyboardChanged(event) { keyboardHeight.value = Math.max(0, Number(event.detail?.height) || 0); }

async function save() {
  if (saving.value || saved.value || !hasChanges.value) return;
  const name = displayName.value.trim();
  if (name.length > 80) { nicknameError.value = '昵称最多填写 80 个字，请缩短后保存。'; return; }
  if (!hasSession()) { error.value = '登录已失效，请重新登录后保存。'; return; }
  saving.value = true;
  error.value = '';
  nicknameError.value = '';
  uni.hideKeyboard?.();
  keyboardHeight.value = 0;
  let nicknameSaved = false;
  try {
    if (name && name !== baselineName.value) {
      saveUser(await api.saveAccountProfile({ displayName: name }));
      baselineName.value = name;
      nicknameSaved = true;
    }
    if (avatarPath.value) {
      await uploadAvatar(avatarPath.value);
      avatarPath.value = '';
    }
    saved.value = true;
    emit('close');
    uni.showToast({ title: '资料已保存', icon: 'success' });
  } catch (detail) {
    const reason = detail.message || '请检查网络后重试。';
    error.value = nicknameSaved ? `昵称已保存，头像未保存。${reason}` : `资料未保存。${reason}`;
  } finally { saving.value = false; }
}

function close() {
  if (saving.value) return;
  uni.hideKeyboard?.();
  keyboardHeight.value = 0;
  emit('close');
}
</script>

<style scoped>
/* finesse · component: account-profile-editor · register=product
 * states: default · hover · focus-visible · active · disabled · loading · error · success
 * tokens: existing clinic palette named in styles/app.css */
.editor-mask { position: fixed; inset: 0; z-index: 940; display: flex; align-items: flex-end; justify-content: center; background: var(--account-mask); }
.editor-panel { display: flex; flex-direction: column; width: 100%; max-width: 560px; max-height: 100%; overflow: hidden; border-radius: 36rpx 36rpx 0 0; background: var(--account-surface); color: var(--account-text); font-family: inherit; }
.editor-header { flex-shrink: 0; display: flex; justify-content: space-between; align-items: flex-start; gap: 20rpx; padding: 36rpx 32rpx 0; }
.editor-title { font-size: var(--account-title-size); font-weight: 650; line-height: 1.4; }
.editor-description { margin-top: 10rpx; color: var(--account-muted); font-size: var(--account-small-size); line-height: 1.6; }
.editor-content { min-height: 0; }
.avatar-section { display: flex; flex-direction: column; align-items: center; padding: 32rpx 32rpx 30rpx; }
.avatar-button { position: relative; flex-shrink: 0; width: 132rpx; height: 132rpx; padding: 2rpx; overflow: visible; border: 1rpx solid var(--account-border); border-radius: 32rpx; background: var(--account-field); outline: 2px solid transparent; outline-offset: 4px; }
.account-avatar { display: block; width: 128rpx; height: 128rpx; border-radius: 30rpx; }
.camera-badge { position: absolute; right: -9rpx; bottom: -9rpx; width: 46rpx; height: 46rpx; display: flex; align-items: center; justify-content: center; border: 4rpx solid var(--account-surface); border-radius: 50%; background: var(--account-accent); }
.camera-icon { position: relative; width: 22rpx; height: 16rpx; border: 2rpx solid var(--account-on-accent); border-radius: 4rpx; }
.camera-icon::before { content: ''; position: absolute; width: 8rpx; height: 4rpx; top: -5rpx; left: 5rpx; border-radius: 2rpx 2rpx 0 0; background: var(--account-on-accent); }
.camera-icon::after { content: ''; position: absolute; width: 6rpx; height: 6rpx; left: 5rpx; top: 3rpx; border: 2rpx solid var(--account-on-accent); border-radius: 50%; }
.avatar-label { margin-top: 19rpx; color: var(--account-accent); font-size: var(--account-body-size); line-height: 1.6; }
.avatar-caption { margin-top: 4rpx; color: var(--account-muted); font-size: var(--account-small-size); line-height: 1.5; }
.privacy-message { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx; margin: 0 32rpx 20rpx; color: var(--account-muted); font-size: var(--account-small-size); line-height: 1.7; }
.text-button { display: inline-flex; align-items: center; min-height: 44px; padding: 0 8rpx; border: 0; background: transparent; color: var(--account-accent); font-size: var(--account-small-size); white-space: nowrap; }
.nickname-field { margin: 0 32rpx; padding: 22rpx 24rpx 8rpx; border: 1rpx solid var(--account-border); border-radius: 20rpx; background: var(--account-field); outline: 2px solid transparent; outline-offset: 2px; }
.field-heading { display: flex; align-items: center; gap: 12rpx; }
.nickname-label { font-size: var(--account-body-size); font-weight: 600; }
.optional-label { color: var(--account-muted); font-size: var(--account-small-size); }
.nickname-control { display: flex; align-items: center; min-width: 0; }
.nickname-input { flex: 1; min-width: 0; height: 88rpx; color: var(--account-text); font-size: var(--account-input-size); }
.nickname-input::placeholder { color: var(--account-muted); }
.icon-button { flex-shrink: 0; display: flex; align-items: center; justify-content: center; width: 88rpx; height: 88rpx; min-width: 44px; min-height: 44px; padding: 0; color: var(--account-muted); background: transparent; font-size: var(--account-icon-size); line-height: 1; border-radius: 16rpx; outline: 2px solid transparent; outline-offset: 1px; }
.close-button { margin: -12rpx -12rpx 0 0; }
.clear-button { margin-right: -12rpx; }
.field-help { min-height: 44rpx; margin: 12rpx 34rpx 0; color: var(--account-muted); font-size: var(--account-small-size); line-height: 1.7; }
.editor-note { margin: 8rpx 34rpx 12rpx; color: var(--account-muted); font-size: var(--account-small-size); line-height: 1.7; }
.editor-footer { flex-shrink: 0; padding: 0 32rpx calc(28rpx + env(safe-area-inset-bottom)); }
.editor-footer.keyboard-visible { padding-bottom: 24rpx; }
.status-slot { min-height: 40rpx; padding: 4rpx 0 8rpx; }
.editor-error { padding: 12rpx 16rpx; border-radius: 12rpx; color: var(--account-danger); background: var(--account-danger-surface); font-size: var(--account-small-size); line-height: 1.6; }
.save-button { width: 100%; min-height: 48px; padding: 24rpx; border: 1rpx solid var(--account-accent); border-radius: 20rpx; background: var(--account-accent); color: var(--account-on-accent); font-size: var(--account-input-size); font-weight: 600; line-height: 1.5; white-space: nowrap; outline: 2px solid transparent; outline-offset: 3px; }
.icon-button::after, .text-button::after, .avatar-button::after, .save-button::after { border: none; }
.nickname-field.is-focused, .nickname-field.is-focus { outline-color: var(--account-accent); }
.nickname-field.is-error { border-color: var(--account-danger); outline-color: var(--account-danger); }
.field-help-error { color: var(--account-danger); }
.avatar-button:focus-visible, .icon-button:focus-visible, .text-button:focus-visible, .save-button:focus-visible, .save-button.is-focus { outline-color: var(--account-accent); }
.avatar-button:active, .icon-button:active, .text-button:active { background: var(--account-pressed-surface); }
.save-button:active:not([disabled]), .save-button.is-active { background: var(--account-accent-pressed); border-color: var(--account-accent-pressed); }
.save-button[disabled], .save-button.is-disabled { opacity: .5; cursor: not-allowed; }
.avatar-button[disabled], .icon-button[disabled] { opacity: .5; cursor: not-allowed; }
.save-button.is-loading { opacity: .75; cursor: progress; }
.save-button.is-success { opacity: 1; color: var(--account-success); border-color: var(--account-success-surface); background: var(--account-success-surface); }
@media (hover: hover) {
  .avatar-button:hover:not([disabled]), .icon-button:hover:not([disabled]), .text-button:hover { background: var(--account-pressed-surface); }
  .save-button:hover:not([disabled]), .save-button.is-hover { background: var(--account-accent-hover); border-color: var(--account-accent-hover); }
}
</style>
