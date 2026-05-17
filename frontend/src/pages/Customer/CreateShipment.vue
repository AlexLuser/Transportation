<template>
    <div class="shipment-container" v-loading="loading">

        <!-- 步骤条 -->
        <el-steps :active="step" align-center class="steps-bar" finish-status="success">
            <el-step title="取件地址" />
            <el-step title="收件地址" />
            <el-step title="货物信息" />
        </el-steps>

        <!-- 步骤1：取件地址 -->
        <el-card v-if="step === 0" class="section-card">
            <template #header>
                <div class="card-header">
                    <span>选择取件地址（运输员将前往此地取货）</span>
                    <el-button link type="primary" @click="goToProfile">前往个人中心添加地址</el-button>
                </div>
            </template>
            <el-empty v-if="addresses.length === 0" description="暂无地址，请先前往个人中心添加" />
            <el-radio-group v-else v-model="senderAddressId" class="address-group">
                <el-radio v-for="addr in addresses" :key="addr.id" :value="addr.id" class="address-radio">
                    <div class="address-row-1">
                        <span class="addr-name">{{ addr.receiverName }}</span>
                        <span class="addr-phone">{{ addr.receiverPhone }}</span>
                        <el-tag v-if="addr.isDefault === 1" type="success" size="small">默认</el-tag>
                    </div>
                    <div class="address-row-2">
                        {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}
                    </div>
                    <div class="address-row-warn" v-if="!addr.latitude || !addr.longitude">
                        <el-icon color="#e6a23c"><WarningFilled /></el-icon>
                        <span>此地址无坐标，Hub分配将降级为同城模式</span>
                    </div>
                </el-radio>
            </el-radio-group>
            <div class="step-footer">
                <el-button type="primary" :disabled="!senderAddressId" @click="step = 1">下一步</el-button>
            </div>
        </el-card>

        <!-- 步骤2：收件地址 -->
        <el-card v-if="step === 1" class="section-card">
            <template #header>
                <div class="card-header">
                    <span>选择收件地址</span>
                    <el-button link type="primary" @click="goToProfile">前往个人中心添加地址</el-button>
                </div>
            </template>
            <el-empty v-if="addresses.length === 0" description="暂无地址，请先前往个人中心添加" />
            <el-radio-group v-else v-model="deliveryAddressId" class="address-group">
                <el-radio v-for="addr in addresses" :key="addr.id" :value="addr.id" class="address-radio">
                    <div class="address-row-1">
                        <span class="addr-name">{{ addr.receiverName }}</span>
                        <span class="addr-phone">{{ addr.receiverPhone }}</span>
                        <el-tag v-if="addr.isDefault === 1" type="success" size="small">默认</el-tag>
                    </div>
                    <div class="address-row-2">
                        {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}
                    </div>
                </el-radio>
            </el-radio-group>
            <div class="step-footer">
                <el-button @click="step = 0">上一步</el-button>
                <el-button type="primary" :disabled="!deliveryAddressId" @click="step = 2">下一步</el-button>
            </div>
        </el-card>

        <!-- 步骤3：货物信息 + 费用预览 + 提交 -->
        <el-card v-if="step === 2" class="section-card">
            <template #header>货物信息</template>
            <el-form :model="cargoForm" label-width="100px" class="cargo-form">
                <el-form-item label="货物名称" required>
                    <el-input v-model="cargoForm.cargoName" placeholder="如：电子产品、服装、文件等" maxlength="100" />
                </el-form-item>
                <el-row :gutter="16">
                    <el-col :span="12">
                        <el-form-item label="重量(kg)">
                            <el-input-number
                                v-model="cargoForm.weight"
                                :min="0.1" :max="100" :precision="2"
                                controls-position="right"
                                style="width: 100%"
                            />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="数量">
                            <el-input-number
                                v-model="cargoForm.quantity"
                                :min="1" :max="999" :precision="0"
                                controls-position="right"
                                style="width: 100%"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-form-item label="申报价值(¥)">
                    <el-input-number
                        v-model="cargoForm.declaredValue"
                        :min="0" :precision="2"
                        controls-position="right"
                        style="width: 200px"
                    />
                    <span class="field-tip">用于保价参考，不计入运费</span>
                </el-form-item>
                <el-form-item label="备注">
                    <el-input
                        v-model="cargoForm.remark"
                        type="textarea" :rows="3" maxlength="200"
                        show-word-limit placeholder="如有特殊要求请在此填写"
                    />
                </el-form-item>
            </el-form>

            <!-- 费用预览 -->
            <div class="fee-preview">
                <div class="fee-row">
                    <span class="fee-label">预估运费</span>
                    <span class="fee-value">¥{{ estimatedFee }}</span>
                </div>
                <div class="fee-tip">基础 ¥12，超出 1kg 部分 ¥2/kg（实际以系统计算为准）</div>
            </div>

            <div class="step-footer">
                <el-button @click="step = 1">上一步</el-button>
                <el-button
                    type="primary"
                    size="large"
                    :loading="submitting"
                    :disabled="!cargoForm.cargoName"
                    @click="submitShipment"
                >
                    提交寄件申请
                </el-button>
            </div>
        </el-card>

    </div>
</template>

<script setup lang="ts" name="CreateShipment">
    import { ref, reactive, computed, onMounted } from 'vue';
    import { useRouter } from 'vue-router';
    import { ElMessage } from 'element-plus';
    import { WarningFilled } from '@element-plus/icons-vue';
    import { getCustomerAddresses } from '@/api/customer';
    import { createPersonalShipment } from '@/api/order';
    import { useUserStore } from '@/stores/userStore';

    const router = useRouter();
    const userStore = useUserStore();

    const step = ref(0);
    const loading = ref(false);
    const submitting = ref(false);
    const addresses = ref<any[]>([]);
    const senderAddressId = ref<number | null>(null);
    const deliveryAddressId = ref<number | null>(null);

    const cargoForm = reactive({
        cargoName: '',
        weight: 1.0,
        declaredValue: 0,
        quantity: 1,
        remark: ''
    });

    const estimatedFee = computed(() => {
        const w = cargoForm.weight ?? 0;
        return (12 + Math.max(0, w - 1) * 2).toFixed(2);
    });

    const fetchAddresses = async () => {
        loading.value = true;
        try {
            const userId = Number(userStore.userInfo?.userId);
            const res = await getCustomerAddresses(userId);
            addresses.value = res.data ?? [];
            const def = addresses.value.find((a: any) => a.isDefault === 1);
            senderAddressId.value = def?.id ?? addresses.value[0]?.id ?? null;
            deliveryAddressId.value = def?.id ?? addresses.value[0]?.id ?? null;
        } finally {
            loading.value = false;
        }
    };

    const submitShipment = async () => {
        if (!senderAddressId.value) {
            ElMessage.warning('请选择取件地址');
            step.value = 0;
            return;
        }
        if (!deliveryAddressId.value) {
            ElMessage.warning('请选择收件地址');
            step.value = 1;
            return;
        }
        if (!cargoForm.cargoName.trim()) {
            ElMessage.warning('请填写货物名称');
            return;
        }
        submitting.value = true;
        try {
            await createPersonalShipment({
                senderAddressId: senderAddressId.value,
                deliveryAddressId: deliveryAddressId.value,
                cargoName: cargoForm.cargoName.trim(),
                weight: cargoForm.weight,
                declaredValue: cargoForm.declaredValue,
                quantity: cargoForm.quantity,
                remark: cargoForm.remark
            });
            ElMessage.success('寄件申请已提交，请前往运单列表支付');
            router.push('/sender/home/orders');
        } finally {
            submitting.value = false;
        }
    };

    const goToProfile = () => router.push('/sender/home/profile');

    onMounted(fetchAddresses);
</script>

<style scoped>
    .shipment-container {
        display: flex;
        flex-direction: column;
        gap: 20px;
        padding-bottom: 40px;
    }

    .steps-bar {
        background: #fff;
        padding: 20px 40px;
        border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0,0,0,.08);
    }

    .section-card { border-radius: 8px; }

    .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .address-group {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 16px;
    }

    .address-radio {
        display: flex;
        align-items: flex-start;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 14px 16px;
        cursor: pointer;
        transition: border-color .2s;
        height: auto;
        margin-right: 0;
    }

    .address-radio:hover { border-color: #409eff; }

    .address-row-1 {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 6px;
    }

    .addr-name { font-weight: 600; font-size: 15px; }
    .addr-phone { color: #606266; font-size: 13px; }

    .address-row-2 {
        font-size: 13px;
        color: #606266;
        line-height: 1.5;
    }

    .address-row-warn {
        display: flex;
        align-items: center;
        gap: 4px;
        margin-top: 6px;
        font-size: 12px;
        color: #e6a23c;
    }

    .step-footer {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid #f0f2f5;
    }

    .cargo-form { padding: 4px 0; }

    .field-tip {
        margin-left: 10px;
        font-size: 12px;
        color: #909399;
    }

    .fee-preview {
        background: #f8f9fa;
        border-radius: 8px;
        padding: 16px 20px;
        margin: 16px 0 0;
    }

    .fee-row {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
    }

    .fee-label { font-size: 14px; color: #606266; }

    .fee-value {
        font-size: 24px;
        font-weight: bold;
        color: #409eff;
    }

    .fee-tip {
        margin-top: 6px;
        font-size: 12px;
        color: #909399;
    }
</style>
