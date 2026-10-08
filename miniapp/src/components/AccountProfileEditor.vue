<template>
  <view v-if="visible" class="editor-mask">
    <view class="editor-panel">
      <view class="editor-title">设置微信昵称与头像</view>
      <view class="editor-description">微信登录不会自动读取昵称和头像。点击昵称输入框可选微信建议的昵称，或自行填写；头像请点击下方按钮选择。</view>
      <view class="field">
        <text class="field-label">账号昵称</text>
        <!-- #ifdef MP-WEIXIN -->
        <input v-model="displayName" class="input" type="nickname" maxlength="80"
          placeholder="点此选微信昵称或自行输入" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <input v-model="displayName" class="input" maxlength="80" placeholder="请输入昵称" />
        <!-- #endif -->
      </view>
      <view class="avatar-row">
        <image v-if="user?.avatarDisplayUrl || user?.avatarUrl" class="account-avatar"
          :src="user.avatarDisplayUrl || user.avatarUrl" mode="aspectFill" />
        <view v-else class="account-avatar placeholder">{{ (user?.displayName || '我').slice(0, 1) }}</view>
        <button class="btn btn-outline avatar-action" @click="avatarVisible = true">选择微信头像或自定义图片</button>
      </view>
      <view class="editor-note">选择头像后会立即上传保存；昵称需点击下方按钮保存。</view>
      <view v-if="error" class="editor-error">{{ error }}</view>
      <button class="btn btn-primary" :loading="saving" :disabled="saving" @click="save">保存昵称</button>
      <button class="btn btn-outline later-action" :disabled="saving" @click="close">{{ changed ? '完成' : '稍后设置' }}</button>
    </view>
    <AvatarPicker :visible="avatarVisible" @close="avatarVisible = false" @success="avatarSaved" />
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { api } from '../utils/request';
import { authState, saveUser } from '../utils/session';
import AvatarPicker from './AvatarPicker.vue';

const props = defineProps({ visible: { type: Boolean, default: false } });
const emit = defineEmits(['close']);
const user = computed(() => authState.user);
const displayName = ref('');
const saving = ref(false);
const changed = ref(false);
const error = ref('');
const avatarVisible = ref(false);

watch(() => props.visible, visible => {
  if (!visible) return;
  displayName.value = user.value?.displayName === '微信用户' ? '' : (user.value?.displayName || '');
  error.value = '';
  changed.value = false;
});

async function save() {
  if (saving.value) return;
  const name = displayName.value.trim();
  if (!name || name.length > 80) { error.value = '请填写 1 至 80 字的昵称'; return; }
  saving.value = true;
  error.value = '';
  try {
    saveUser(await api.saveAccountProfile({ displayName: name }));
    changed.value = true;
    uni.showToast({ title: '昵称已保存', icon: 'success' });
  } catch (detail) { error.value = detail.message || '昵称未保存，请重试'; }
  finally { saving.value = false; }
}

function avatarSaved() { changed.value = true; }
function close() { avatarVisible.value = false; emit('close'); }
</script>

<style scoped>
.editor-mask { position: fixed; inset: 0; z-index: 940; display: flex; align-items: center; justify-content: center; padding: 45rpx; background: rgba(20, 46, 36, .48); }
.editor-panel { width: 100%; max-height: 88vh; overflow-y: auto; padding: 38rpx 32rpx; background: #fff; border-radius: 28rpx; box-sizing: border-box; }
.editor-title { font-size: 34rpx; font-weight: 600; color: #244c3c; }
.editor-description { margin: 16rpx 0 26rpx; font-size: 25rpx; line-height: 1.7; color: #799082; }
.avatar-row { display: flex; align-items: center; gap: 18rpx; margin: 24rpx 0; }
.account-avatar { width: 80rpx; height: 80rpx; border-radius: 40rpx; flex-shrink: 0; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #fff; background: #12836d; font-size: 32rpx; }
.avatar-action { flex: 1; margin: 0; font-size: 23rpx; }
.editor-note { color: #799082; font-size: 22rpx; line-height: 1.6; }
.editor-error { margin: 16rpx 0; color: #a55a45; font-size: 24rpx; }
.later-action { margin-top: 16rpx; }
</style>
