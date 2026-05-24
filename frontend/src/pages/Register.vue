<template>
    <div class="register-container">
        <el-card class="register-card">
            <h2>用户注册</h2>
            <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" label-position="right">

                <!-- 基础信息 -->
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="form.username" placeholder="请输入用户名（4-20位字母或数字）" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="form.password" type="password" show-password placeholder="请输入密码（至少6位）" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
                </el-form-item>
                <el-form-item label="注册角色" prop="role">
                    <el-radio-group v-model="form.role">
                        <el-radio value="customer">顾客</el-radio>
                        <el-radio value="shop">商户</el-radio>
                        <el-radio value="driver">司机</el-radio>
                    </el-radio-group>
                </el-form-item>

                <!-- 商户专属字段 -->
                <template v-if="form.role === 'shop'">
                    <el-divider content-position="left">
                        <span class="divider-text">商户信息（需管理员审核）</span>
                    </el-divider>
                    <el-form-item label="商户名称" prop="shopName">
                        <el-input v-model="form.shopName" placeholder="请输入商户/公司名称" />
                    </el-form-item>
                    <el-form-item label="联系电话" prop="shopPhone">
                        <el-input v-model="form.shopPhone" placeholder="请输入商户联系电话" />
                    </el-form-item>
                    <el-form-item label="商户邮箱">
                        <el-input v-model="form.shopEmail" placeholder="请输入商户邮箱（选填）" />
                    </el-form-item>
                    <el-form-item label="营业执照号" prop="businessLicense">
                        <el-input v-model="form.businessLicense" placeholder="请输入营业执照号" />
                    </el-form-item>
                </template>

                <!-- 司机专属字段 -->
                <template v-if="form.role === 'driver'">
                    <el-divider content-position="left">
                        <span class="divider-text">驾驶证信息（需管理员审核）</span>
                    </el-divider>
                    <el-form-item label="真实姓名" prop="realName">
                        <el-input v-model="form.realName" placeholder="请输入真实姓名" />
                    </el-form-item>
                    <el-form-item label="手机号" prop="phone">
                        <el-input v-model="form.phone" placeholder="请输入手机号" />
                    </el-form-item>
                    <el-form-item label="邮箱">
                        <el-input v-model="form.email" placeholder="请输入邮箱（选填）" />
                    </el-form-item>
                    <el-form-item label="驾驶证号" prop="licenseNumber">
                        <el-input v-model="form.licenseNumber" placeholder="请输入驾驶证号" />
                    </el-form-item>
                    <el-form-item label="驾驶证类型" prop="licenseType">
                        <el-select v-model="form.licenseType" placeholder="请选择驾驶证类型" style="width: 100%">
                            <el-option label="C1" value="C1" />
                            <el-option label="C2" value="C2" />
                            <el-option label="B1" value="B1" />
                            <el-option label="B2" value="B2" />
                            <el-option label="A1" value="A1" />
                            <el-option label="A2" value="A2" />
                        </el-select>
                    </el-form-item>
                    <el-form-item label="驾驶证到期日" prop="licenseExpireDate">
                        <el-date-picker
                            v-model="form.licenseExpireDate"
                            type="date"
                            placeholder="请选择驾驶证到期日期"
                            value-format="YYYY-MM-DD"
                            style="width: 100%"
                        />
                    </el-form-item>
                </template>

                <!-- 提示信息 -->
                <el-alert
                    v-if="form.role === 'shop' || form.role === 'driver'"
                    type="warning"
                    :closable="false"
                    style="margin-bottom: 16px;"
                >
                    <template #title>
                        {{ form.role === 'shop' ? '商户' : '司机' }}注册后需等待管理员审核，审核通过后方可登录系统。
                    </template>
                </el-alert>
                <el-alert v-if="form.role === 'customer'" type="success" :closable="false" style="margin-bottom: 16px;">
                    <template #title>顾客注册后可直接登录。</template>
                </el-alert>

                <el-form-item>
                    <el-button type="primary" :loading="loading" @click="handleRegister" style="width: 100%;">
                        立即注册
                    </el-button>
                </el-form-item>
                <el-form-item>
                    <el-button link @click="router.push('/login')" style="width: 100%;">
                        已有账号？返回登录
                    </el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script setup lang="ts" name="Register">
    import { ref, reactive } from 'vue';
    import type { FormInstance, FormRules } from 'element-plus';
    import { ElMessage } from 'element-plus';
    import { router } from '@/router';
    import { register } from '@/api/auth';

    const formRef = ref<FormInstance>();
    const loading = ref(false);

    const form = reactive({
        username: '',
        password: '',
        confirmPassword: '',
        role: 'customer',
        // 商户字段
        shopName: '',
        shopPhone: '',
        shopEmail: '',
        businessLicense: '',
        // 司机字段
        realName: '',
        phone: '',
        email: '',
        licenseNumber: '',
        licenseType: '',
        licenseExpireDate: '',
    });

    const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
        if (value !== form.password) {
            callback(new Error('两次输入的密码不一致'));
        } else {
            callback();
        }
    };

    const rules = reactive<FormRules>({
        username: [
            { required: true, message: '请输入用户名', trigger: 'blur' },
            { min: 4, max: 20, message: '用户名长度为 4~20 位', trigger: 'blur' },
        ],
        password: [
            { required: true, message: '请输入密码', trigger: 'blur' },
            { min: 6, message: '密码至少6位', trigger: 'blur' },
        ],
        confirmPassword: [
            { required: true, message: '请确认密码', trigger: 'blur' },
            { validator: validateConfirmPassword, trigger: 'blur' },
        ],
        role: [{ required: true, message: '请选择注册角色', trigger: 'change' }],
        shopName: [{ required: true, message: '请输入商户名称', trigger: 'blur' }],
        shopPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
        businessLicense: [{ required: true, message: '请输入营业执照号', trigger: 'blur' }],
        realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
        phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
        licenseNumber: [{ required: true, message: '请输入驾驶证号', trigger: 'blur' }],
        licenseType: [{ required: true, message: '请选择驾驶证类型', trigger: 'change' }],
        licenseExpireDate: [{ required: true, message: '请选择驾驶证到期日期', trigger: 'change' }],
    });

    const handleRegister = async () => {
        if (!formRef.value) return;
        const valid = await formRef.value.validate().catch(() => false);
        if (!valid) return;

        loading.value = true;
        try {
            await register({
                username: form.username,
                password: form.password,
                role: form.role,
                shopName: form.shopName,
                shopPhone: form.shopPhone,
                shopEmail: form.shopEmail,
                businessLicense: form.businessLicense,
                realName: form.realName,
                phone: form.phone,
                email: form.email,
                licenseNumber: form.licenseNumber,
                licenseType: form.licenseType,
                licenseExpireDate: form.licenseExpireDate,
            });

            if (form.role === 'customer') {
                ElMessage.success('注册成功，请登录！');
            } else {
                ElMessage.success('注册成功，请等待管理员审核！');
            }
            router.push('/login');
        } finally {
            loading.value = false;
        }
    };
</script>

<style scoped>
    .register-container {
        display: flex;
        justify-content: center;
        align-items: flex-start;
        min-height: 100vh;
        padding: 40px 16px;
        background-image: url('@/assets/images/LoginBackground.png');
        background-size: cover;
        background-position: center;
    }

    .register-card {
        width: 520px;
        background-color: rgba(255, 255, 255, 0.93);
        backdrop-filter: blur(10px);
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
        border-radius: 8px;
    }

    h2 {
        text-align: center;
        margin-bottom: 24px;
        color: #409eff;
        font-size: 22px;
    }

    .divider-text {
        font-size: 13px;
        color: #909399;
    }
</style>
