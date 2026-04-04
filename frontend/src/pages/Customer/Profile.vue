<template>
    <div class="profile-container">

        <!-- 头像 & 基本信息头部 -->
        <el-card class="header-card">
            <div class="profile-header">
                <div class="avatar-wrapper" @click="triggerUpload" title="点击更换头像">
                    <el-avatar :size="88" :src="customerInfo?.avatar || undefined">
                        <el-icon :size="40"><UserFilled /></el-icon>
                    </el-avatar>
                    <div class="avatar-overlay">
                        <el-icon :size="20"><Camera /></el-icon>
                        <span>更换头像</span>
                    </div>
                    <input ref="fileInputRef" type="file" accept="image/*" class="hidden-input" @change="handleAvatarChange" />
                </div>
                <div class="header-info">
                    <div class="header-name">{{ customerInfo?.realName || userStore.userInfo?.username || '未设置姓名' }}</div>
                    <div class="header-meta">
                        <span v-if="customerInfo?.phone" class="meta-item">
                            <el-icon><Phone /></el-icon>{{ customerInfo.phone }}
                        </span>
                        <span v-if="customerInfo?.email" class="meta-item">
                            <el-icon><Message /></el-icon>{{ customerInfo.email }}
                        </span>
                        <span v-if="customerInfo?.birthday" class="meta-item">
                            <el-icon><Calendar /></el-icon>{{ formatDate(customerInfo.birthday) }}
                        </span>
                        <span class="meta-item gender-tag">
                            {{ customerInfo?.gender === 1 ? '男' : customerInfo?.gender === 2 ? '女' : '性别未知' }}
                        </span>
                    </div>
                </div>
            </div>
        </el-card>

        <!-- Tabs 主体 -->
        <el-card class="tabs-card">
            <el-tabs v-model="activeTab">

                <!-- Tab 1: 个人信息 -->
                <el-tab-pane label="个人信息" name="info">
                    <div class="tab-action-row">
                        <el-button v-if="!isEditing" type="primary" plain size="small" @click="isEditing = true">
                            <el-icon><Edit /></el-icon>&nbsp;编辑
                        </el-button>
                    </div>

                    <el-descriptions v-if="!isEditing" :column="2" border>
                        <el-descriptions-item label="真实姓名">{{ customerInfo?.realName || '—' }}</el-descriptions-item>
                        <el-descriptions-item label="手机号">{{ customerInfo?.phone || '—' }}</el-descriptions-item>
                        <el-descriptions-item label="邮箱">{{ customerInfo?.email || '—' }}</el-descriptions-item>
                        <el-descriptions-item label="性别">
                            {{ customerInfo?.gender === 1 ? '男' : customerInfo?.gender === 2 ? '女' : '未知' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="生日" :span="2">
                            {{ customerInfo?.birthday ? formatDate(customerInfo.birthday) : '—' }}
                        </el-descriptions-item>
                    </el-descriptions>

                    <el-form v-else :model="customerInfo" label-width="90px">
                        <el-form-item label="真实姓名">
                            <el-input v-model="customerInfo.realName" placeholder="请输入真实姓名" />
                        </el-form-item>
                        <el-form-item label="手机号">
                            <el-input v-model="customerInfo.phone" placeholder="请输入手机号" />
                        </el-form-item>
                        <el-form-item label="邮箱">
                            <el-input v-model="customerInfo.email" placeholder="请输入邮箱" />
                        </el-form-item>
                        <el-form-item label="性别">
                            <el-select v-model="customerInfo.gender" class="full-width">
                                <el-option label="未知" :value="0" />
                                <el-option label="男" :value="1" />
                                <el-option label="女" :value="2" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="生日">
                            <el-date-picker
                                v-model="customerInfo.birthday"
                                type="date"
                                placeholder="请选择生日"
                                value-format="YYYY-MM-DD"
                                class="full-width"
                            />
                        </el-form-item>
                        <el-form-item>
                            <el-button type="primary" @click="updateInfo">保存</el-button>
                            <el-button @click="isEditing = false">取消</el-button>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>

                <!-- Tab 2: 收货地址 -->
                <el-tab-pane label="收货地址" name="address">
                    <div class="tab-action-row">
                        <el-button type="primary" plain size="small" @click="openAddDialog">
                            <el-icon><Plus /></el-icon>&nbsp;添加地址
                        </el-button>
                    </div>

                    <el-empty v-if="addresses.length === 0" description="暂无收货地址" :image-size="80" />
                    <div v-else class="address-list">
                        <div class="address-item" v-for="item in addresses" :key="item.id">
                            <div class="address-left">
                                <el-tag v-if="item.isDefault === 1" type="success" size="small" class="default-tag">默认</el-tag>
                                <div class="address-receiver">
                                    <span class="receiver-name">{{ item.receiverName }}</span>
                                    <span class="receiver-phone">{{ item.receiverPhone }}</span>
                                </div>
                                <div class="address-detail">
                                    {{ item.province }}{{ item.city }}{{ item.district }}{{ item.detailAddress }}
                                    <span v-if="item.postalCode" class="postal-code">{{ item.postalCode }}</span>
                                </div>
                            </div>
                            <div class="address-actions">
                                <el-button size="small" plain @click="openEditDialog(item)">编辑</el-button>
                                <el-popconfirm title="确定删除这条地址吗？" @confirm="confirmDeleteAddress(item)">
                                    <template #reference>
                                        <el-button size="small" type="danger" plain>删除</el-button>
                                    </template>
                                </el-popconfirm>
                            </div>
                        </div>
                    </div>
                </el-tab-pane>

            </el-tabs>
        </el-card>

        <!-- 新增/编辑地址弹窗 -->
        <el-dialog
            v-model="dialogVisible"
            :title="isEditingAddress ? '编辑地址' : '添加地址'"
            width="480px"
            :close-on-click-modal="false"
            destroy-on-close
        >
            <el-form :model="address" label-width="90px">
                <el-form-item label="收货人">
                    <el-input v-model="address.receiverName" placeholder="请输入收货人姓名" />
                </el-form-item>
                <el-form-item label="手机号">
                    <el-input v-model="address.receiverPhone" placeholder="请输入手机号" />
                </el-form-item>
                <el-form-item label="省/市/区">
                    <el-cascader
                        v-model="selectedRegion"
                        :options="pcaTextArr"
                        @change="handleRegionChange"
                        placeholder="请选择省/市/区"
                        class="full-width"
                    />
                </el-form-item>
                <el-form-item label="详细地址">
                    <el-input v-model="address.detailAddress" placeholder="请输入详细地址" />
                </el-form-item>
                <el-form-item label="邮编">
                    <el-input v-model="address.postalCode" placeholder="邮编（选填）" />
                </el-form-item>
                <el-form-item label="设为默认">
                    <el-switch v-model="address.isDefault" :active-value="1" :inactive-value="0" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="confirmAddress">确定</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="CustomerProfile">
    import { ref, onMounted } from 'vue';
    import { UserFilled, Camera, Phone, Message, Calendar, Edit, Plus } from '@element-plus/icons-vue';
    import { getCustomerInfo, updateCustomerInfo, getCustomerAddresses, addCustomerAddress, updateCustomerAddress, deleteCustomerAddress } from '@/api/customer';
    import { ElMessage } from 'element-plus';
    import { pcaTextArr } from 'element-china-area-data';
    import { useUserStore } from '@/stores/userStore';

    const userStore = useUserStore();

    const customerInfo = ref<any>(null);
    const addresses = ref<any[]>([]);
    const address = ref<any>({});
    const selectedRegion = ref<string[]>([]);
    const fileInputRef = ref<HTMLInputElement | null>(null);

    const activeTab = ref('info');
    const isEditing = ref(false);
    const dialogVisible = ref(false);
    const isEditingAddress = ref(false);

    const formatDate = (val: string | Date) => {
        if (!val) return '—';
        const s = typeof val === 'string' ? val : val.toISOString();
        return s.slice(0, 10);
    }

    const getInfo = async () => {
        try {
            const res = await getCustomerInfo(Number(userStore.userInfo?.userId));
            customerInfo.value = res.data;
        } catch (error) {
            console.error(error);
        }
    }

    const updateInfo = async () => {
        try {
            const res = await updateCustomerInfo(customerInfo.value);
            customerInfo.value = res.data;
            ElMessage.success('个人信息更新成功');
        } catch (error) {
            ElMessage.error('更新失败，请重试');
            console.error(error);
        } finally {
            isEditing.value = false;
        }
    }

    const triggerUpload = () => {
        fileInputRef.value?.click();
    }

    const handleAvatarChange = async (event: Event) => {
        const file = (event.target as HTMLInputElement).files?.[0];
        if (!file) return;
        if (file.size > 2 * 1024 * 1024) {
            ElMessage.warning('图片大小不能超过 2MB');
            return;
        }
        const reader = new FileReader();
        reader.onload = async (e) => {
            const base64 = e.target?.result as string;
            try {
                const updated = { ...customerInfo.value, avatar: base64 };
                const res = await updateCustomerInfo(updated);
                customerInfo.value = res.data;
                ElMessage.success('头像更新成功');
            } catch (error) {
                ElMessage.error('头像上传失败，请重试');
                console.error(error);
            }
        };
        reader.readAsDataURL(file);
        (event.target as HTMLInputElement).value = '';
    }

    const getAddresses = async () => {
        try {
            const res = await getCustomerAddresses(Number(userStore.userInfo?.userId));
            addresses.value = res.data;
        } catch (error) {
            console.error(error);
        }
    }

    const handleRegionChange = (codes: string[]) => {
        address.value.province = codes[0];
        address.value.city = codes[1];
        address.value.district = codes[2];
    }

    const openAddDialog = () => {
        address.value = {};
        selectedRegion.value = [];
        isEditingAddress.value = false;
        dialogVisible.value = true;
    }

    const openEditDialog = (item: any) => {
        address.value = { ...item };
        selectedRegion.value = [item.province, item.city, item.district].filter(Boolean);
        isEditingAddress.value = true;
        dialogVisible.value = true;
    }

    const confirmAddress = async () => {
        try {
            if (isEditingAddress.value) {
                await updateCustomerAddress(address.value);
                ElMessage.success('地址更新成功');
            } else {
                await addCustomerAddress(address.value);
                ElMessage.success('地址添加成功');
            }
            dialogVisible.value = false;
            await getAddresses();
        } catch (error) {
            ElMessage.error('操作失败，请重试');
            console.error(error);
        }
    }

    const confirmDeleteAddress = async (item: any) => {
        try {
            await deleteCustomerAddress(item.id);
            ElMessage.success('地址删除成功');
            await getAddresses();
        } catch (error) {
            ElMessage.error('删除失败，请重试');
            console.error(error);
        }
    }

    onMounted(() => {
        getInfo();
        getAddresses();
    })
</script>

<style scoped>
.profile-container {
    max-width: 820px;
    margin: 24px auto;
    padding: 0 16px;
}

/* 头部卡片 */
.header-card {
    margin-bottom: 16px;
}

.profile-header {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 8px 0;
}

.avatar-wrapper {
    position: relative;
    flex-shrink: 0;
    cursor: pointer;
    border-radius: 50%;
    overflow: hidden;
    width: 88px;
    height: 88px;
}

.avatar-wrapper .el-avatar {
    display: block;
    transition: filter 0.2s;
}

.avatar-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.45);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: #fff;
    font-size: 11px;
    opacity: 0;
    transition: opacity 0.2s;
}

.avatar-wrapper:hover .avatar-overlay {
    opacity: 1;
}

.hidden-input {
    display: none;
}

.header-info {
    flex: 1;
}

.header-name {
    font-size: 20px;
    font-weight: 700;
    color: #1a1a1a;
    margin-bottom: 8px;
}

.header-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    color: #666;
    font-size: 13px;
}

.meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
}

.gender-tag {
    color: #888;
}

/* Tabs 卡片 */
.tabs-card {
    margin-bottom: 16px;
}

.tab-action-row {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 14px;
}

/* 地址列表 */
.address-list {
    display: flex;
    flex-direction: column;
}

.address-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 14px 4px;
    border-bottom: 1px solid #f2f2f2;
    transition: background 0.15s;
}

.address-item:last-child {
    border-bottom: none;
}

.address-item:hover {
    background: #fafafa;
}

.address-left {
    flex: 1;
    min-width: 0;
}

.default-tag {
    margin-bottom: 4px;
}

.address-receiver {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 4px;
}

.receiver-name {
    font-weight: 600;
    font-size: 15px;
    color: #222;
}

.receiver-phone {
    color: #666;
    font-size: 13px;
}

.address-detail {
    color: #555;
    font-size: 13px;
    line-height: 1.5;
}

.postal-code {
    color: #999;
    margin-left: 8px;
    font-size: 12px;
}

.address-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
    margin-left: 16px;
}

.full-width {
    width: 100%;
}
</style>
