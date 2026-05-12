<template>
    <div class="product-manage">

        <!-- 顶部操作栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <el-input
                    v-model="keyword"
                    placeholder="搜索货物名称 / 编码"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
                    <el-option label="启用" :value="1" />
                    <el-option label="停用" :value="0" />
                    <el-option label="待审核" :value="2" />
                </el-select>
                <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>

                <!-- 货物数量统计 -->
                <div class="stat-tags">
                    <el-tag type="info" size="small">全部 {{ statsTotal }}</el-tag>
                    <el-tag type="success" size="small">启用 {{ statsOnSale }}</el-tag>
                    <el-tag type="info" effect="plain" size="small">停用 {{ statsOffSale }}</el-tag>
                    <el-tag type="warning" size="small">待审核 {{ statsPending }}</el-tag>
                </div>
            </div>
            <el-button type="success" :icon="Plus" @click="openAddDialog">新增货物</el-button>
        </div>

        <!-- 货物表格 -->
        <el-card shadow="never" class="table-card">
            <el-table :data="products" v-loading="loading" row-key="id" stripe>
                <el-table-column label="货物名称" prop="productName" min-width="160" show-overflow-tooltip />
                <el-table-column label="货物编码" prop="productCode" width="130" show-overflow-tooltip />
                <el-table-column label="申报价值" width="110">
                    <template #default="{ row }">
                        <span class="price-text">¥{{ row.price }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="计量单位" prop="unit" width="90" />
                <el-table-column label="重量(kg)" prop="weight" width="100" />
                <el-table-column label="已发件数" prop="salesCount" width="90" />
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="statusTagType(row.status)" size="small">
                            {{ statusLabel(row.status) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="创建时间" width="160">
                    <template #default="{ row }">
                        {{ formatDate(row.createTime) }}
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" @click="openDetailDialog(row)">详情</el-button>
                        <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="pagination-bar">
                <el-pagination
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                    :page-sizes="[10, 20, 50]"
                    :total="total"
                    layout="total, sizes, prev, pager, next"
                    @current-change="fetchProducts"
                    @size-change="(val: number) => { pageSize = val; currentPage = 1; fetchProducts(); }"
                />
            </div>
        </el-card>

        <!-- ===================== 详情弹窗 ===================== -->
        <el-dialog v-model="detailVisible" title="货物详情" width="780px" align-center>
            <div v-if="detailProduct" class="detail-body">
                <!-- 左：图片 -->
                <div class="detail-img-wrap">
                    <el-image :src="getFirstImage(detailProduct.images)" fit="cover" class="detail-image">
                        <template #error>
                            <div class="img-placeholder"><el-icon size="48"><Picture /></el-icon></div>
                        </template>
                    </el-image>
                </div>

                <!-- 右：信息 -->
                <div class="detail-info">
                    <div class="detail-name">{{ detailProduct.productName }}</div>
                    <div class="detail-price-row">
                        <span class="price-label">申报价值</span>
                        <span class="price-text lg">¥{{ detailProduct.price }}</span>
                    </div>

                    <el-descriptions :column="2" border size="small" class="detail-desc">
                        <el-descriptions-item label="货物编码">{{ detailProduct.productCode ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="货物分类">{{ detailProduct.categoryId ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="计量单位">{{ detailProduct.unit ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="重量">{{ detailProduct.weight ? detailProduct.weight + ' kg' : '-' }}</el-descriptions-item>
                        <el-descriptions-item label="已发件数">{{ detailProduct.salesCount ?? 0 }} 件</el-descriptions-item>
                        <el-descriptions-item label="状态">
                            <el-tag :type="statusTagType(detailProduct.status)" size="small">
                                {{ statusLabel(detailProduct.status) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="创建时间" :span="2">{{ formatDate(detailProduct.createTime) }}</el-descriptions-item>
                        <el-descriptions-item label="货物描述" :span="2">{{ detailProduct.description ?? '-' }}</el-descriptions-item>
                    </el-descriptions>

                    <!-- 库存信息 -->
                    <div class="stock-section">
                        <div class="stock-title">各仓库库存</div>
                        <el-table :data="detailStocks" v-loading="stockLoading" size="small" border>
                            <el-table-column label="仓库名称" min-width="140">
                                <template #default="{ row }">
                                    {{ warehouseMap[row.warehouseId] ?? ('仓库 #' + row.warehouseId) }}
                                </template>
                            </el-table-column>
                            <el-table-column label="库存数量" prop="stock" width="100" align="center">
                                <template #default="{ row }">
                                    <span :class="row.stock === 0 ? 'stock-zero' : 'stock-normal'">{{ row.stock }}</span>
                                </template>
                            </el-table-column>
                        </el-table>
                        <div v-if="!stockLoading && detailStocks.length === 0" class="stock-empty">暂无库存记录</div>
                    </div>
                </div>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
                <el-button type="primary" @click="openEditFromDetail">编辑货物</el-button>
            </template>
        </el-dialog>

        <!-- ===================== 新增 / 编辑弹窗 ===================== -->
        <el-dialog
            v-model="formVisible"
            :title="isEdit ? '编辑货物' : '新增货物'"
            width="600px"
            align-center
            @closed="resetForm"
        >
            <el-form
                ref="formRef"
                :model="formData"
                :rules="formRules"
                label-width="90px"
                class="product-form"
            >
                <el-row :gutter="16">
                    <el-col :span="24">
                        <el-form-item label="货物名称" prop="productName">
                            <el-input v-model="formData.productName" placeholder="请输入货物名称" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="货物编码">
                            <el-input v-model="formData.productCode" placeholder="货物编码（选填）" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="货物分类">
                            <el-input-number v-model="formData.categoryId" :min="1" :precision="0" placeholder="选填" style="width:100%" controls-position="right" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="申报价值" prop="price">
                            <el-input-number v-model="formData.price" :min="0" :precision="2" placeholder="请输入申报价值" style="width:100%" controls-position="right" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="计量单位">
                            <el-input v-model="formData.unit" placeholder="件 / 箱 / kg…" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="重量(kg)">
                            <el-input-number v-model="formData.weight" :min="0" :precision="3" placeholder="选填" style="width:100%" controls-position="right" />
                        </el-form-item>
                    </el-col>
                    <!-- 编辑模式才显示启用/停用切换，新增不显示（后端默认待审核） -->
                    <el-col :span="24" v-if="isEdit">
                        <el-form-item label="状态">
                            <el-radio-group v-model="formData.status">
                                <el-radio :value="1">启用</el-radio>
                                <el-radio :value="0">停用</el-radio>
                            </el-radio-group>
                            <span class="status-hint" v-if="formData.status === 2">（当前待审核，保存后不变）</span>
                        </el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="货物图片">
                            <el-upload
                                v-model:file-list="uploadFileList"
                                list-type="picture-card"
                                :auto-upload="false"
                                accept="image/*"
                                :limit="5"
                                :on-change="handleImageChange"
                                :on-exceed="() => ElMessage.warning('最多上传 5 张图片')"
                            >
                                <el-icon><Plus /></el-icon>
                                <template #tip>
                                    <div class="upload-tip">最多 5 张，支持 jpg/png/gif，本地预览</div>
                                </template>
                            </el-upload>
                        </el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="货物描述">
                            <el-input
                                v-model="formData.description"
                                type="textarea"
                                :rows="3"
                                placeholder="请输入货物描述（选填）"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>
            </el-form>
            <template #footer>
                <el-button @click="formVisible = false">取消</el-button>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">
                    {{ isEdit ? '保存修改' : '确认新增' }}
                </el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="ShopProduct">
    import { ref, reactive, onMounted } from 'vue';
    import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile, type UploadFile } from 'element-plus';
    import { Search, Plus, Picture } from '@element-plus/icons-vue';
    import {
        getProducts, addProduct, updateProduct, deleteProduct,
        getStockByProduct, getWarehouses,
        type Product, type Warehouse,
    } from '@/api/shop';

    // ==================== 列表状态 ====================
    const loading = ref(false);
    const products = ref<Product[]>([]);
    const keyword = ref('');
    const statusFilter = ref<number | null>(null);
    const currentPage = ref(1);
    const pageSize = ref(10);
    const total = ref(0);

    // 各状态商品数量统计
    const statsTotal   = ref(0);
    const statsOnSale  = ref(0);  // status=1 上架
    const statsOffSale = ref(0);  // status=0 下架
    const statsPending = ref(0);  // status=2 待审核

    // 仓库 id -> 名称 映射（详情弹窗用）
    const warehouseMap = ref<Record<number, string>>({});

    /** 加载各状态商品数量（全量，不受当前筛选条件影响） */
    const loadStats = async () => {
        try {
            const [all, onSale, offSale, pending] = await Promise.all([
                getProducts({ current: 1, size: 1 }),
                getProducts({ current: 1, size: 1, status: 1 }),
                getProducts({ current: 1, size: 1, status: 0 }),
                getProducts({ current: 1, size: 1, status: 2 }),
            ]);
            statsTotal.value   = all.data?.total   ?? 0;
            statsOnSale.value  = onSale.data?.total  ?? 0;
            statsOffSale.value = offSale.data?.total ?? 0;
            statsPending.value = pending.data?.total ?? 0;
        } catch { /* 非致命 */ }
    };

    const fetchProducts = async () => {
        loading.value = true;
        try {
            const res = await getProducts({
                current: currentPage.value,
                size: pageSize.value,
                keyword: keyword.value || undefined,
                status: statusFilter.value !== null ? statusFilter.value : undefined,
                sortField: 'createTime',
                sortOrder: 'desc',
            });
            products.value = res.data?.records ?? [];
            total.value = res.data?.total ?? 0;
        } finally {
            loading.value = false;
        }
    };

    const handleSearch = () => {
        currentPage.value = 1;
        fetchProducts();
        loadStats();
    };

    // ==================== 仓库数据 ====================
    const loadWarehouses = async () => {
        try {
            const res = await getWarehouses();
            const list: Warehouse[] = res.data ?? [];
            list.forEach(w => {
                if (w.id) warehouseMap.value[w.id] = w.warehouseName;
            });
        } catch { /* ignore */ }
    };

    // ==================== 详情弹窗 ====================
    const detailVisible = ref(false);
    const detailProduct = ref<Product | null>(null);
    const detailStocks = ref<any[]>([]);
    const stockLoading = ref(false);

    const openDetailDialog = async (row: Product) => {
        detailProduct.value = row;
        detailStocks.value = [];
        detailVisible.value = true;
        if (row.id) {
            stockLoading.value = true;
            try {
                const res = await getStockByProduct(row.id as number);
                detailStocks.value = res.data ?? [];
            } finally {
                stockLoading.value = false;
            }
        }
    };

    const openEditFromDetail = () => {
        if (detailProduct.value) {
            detailVisible.value = false;
            openEditDialog(detailProduct.value);
        }
    };

    // ==================== 新增 / 编辑弹窗 ====================
    const formVisible = ref(false);
    const isEdit = ref(false);
    const submitting = ref(false);
    const formRef = ref<FormInstance>();

    // 图片上传文件列表
    const uploadFileList = ref<UploadUserFile[]>([]);

    const emptyForm = (): Product => ({
        productName: '',
        productCode: '',
        categoryId: undefined,
        price: 0,
        originalPrice: undefined,
        unit: '',
        weight: undefined,
        images: '',
        status: 2,
        description: '',
    });

    const formData = reactive<Product>(emptyForm());

    const formRules: FormRules = {
        productName: [{ required: true, message: '请输入货物名称', trigger: 'blur' }],
        price: [{ required: true, message: '请输入申报价值', trigger: 'blur' }],
    };

    // 图片选择时读取为 DataURL 用于本地预览
    const handleImageChange = (file: UploadFile) => {
        if (!file.raw) return;
        const reader = new FileReader();
        reader.onload = (e) => {
            const target = uploadFileList.value.find(f => f.uid === file.uid);
            if (target) target.url = e.target?.result as string;
        };
        reader.readAsDataURL(file.raw);
    };

    // 将 uploadFileList 中的 url 序列化为 JSON 字符串存入 formData.images
    const buildImagesJson = () => {
        const urls = uploadFileList.value
            .map(f => f.url)
            .filter(Boolean) as string[];
        return urls.length > 0 ? JSON.stringify(urls) : '';
    };

    // 解析已有图片 JSON，还原为文件列表（仅含 url，用于回显）
    const parseImagesToFileList = (images: string | undefined): UploadUserFile[] => {
        if (!images) return [];
        try {
            const arr: string[] = JSON.parse(images);
            return arr.map((url, i) => ({
                uid: -(i + 1),
                name: `image_${i + 1}`,
                status: 'success',
                url,
            }));
        } catch {
            return [];
        }
    };

    const openAddDialog = () => {
        isEdit.value = false;
        Object.assign(formData, emptyForm());
        uploadFileList.value = [];
        formVisible.value = true;
    };

    const openEditDialog = (row: Product) => {
        isEdit.value = true;
        Object.assign(formData, { ...row });
        uploadFileList.value = parseImagesToFileList(row.images);
        formVisible.value = true;
    };

    const resetForm = () => {
        formRef.value?.resetFields();
        uploadFileList.value = [];
    };

    const handleSubmit = async () => {
        await formRef.value?.validate();
        submitting.value = true;
        try {
            const payload: Product = { ...formData, images: buildImagesJson() };
            if (isEdit.value) {
                await updateProduct(payload);
                ElMessage.success('货物修改成功');
            } else {
                await addProduct(payload);
                ElMessage.success('货物新增成功');
            }
            formVisible.value = false;
            fetchProducts();
        } finally {
            submitting.value = false;
        }
    };

    // ==================== 删除 ====================
    const handleDelete = (row: Product) => {
        ElMessageBox.confirm(
            `确定要删除货物「${row.productName}」吗？此操作不可恢复。`,
            '删除确认',
            { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
        ).then(async () => {
            await deleteProduct(row.id as number);
            ElMessage.success('删除成功');
            fetchProducts();
        }).catch(() => {});
    };

    // ==================== 工具函数 ====================
    const statusLabel = (status: number | undefined) => {
        if (status === 1) return '启用';
        if (status === 0) return '停用';
        if (status === 2) return '待审核';
        return '-';
    };

    const statusTagType = (status: number | undefined) => {
        if (status === 1) return 'success';
        if (status === 0) return 'info';
        if (status === 2) return 'warning';
        return '';
    };

    const getFirstImage = (images: string | undefined) => {
        if (!images) return '';
        try {
            const arr = JSON.parse(images);
            return Array.isArray(arr) && arr.length > 0 ? arr[0] : '';
        } catch {
            return '';
        }
    };

    const formatDate = (date: string | undefined) => {
        if (!date) return '-';
        return new Date(date).toLocaleString('zh-CN', { hour12: false });
    };

    // ==================== 初始化 ====================
    onMounted(() => {
        fetchProducts();
        loadWarehouses();
        loadStats();
    });
</script>

<style scoped>
    .product-manage {
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    /* 工具栏 */
    .toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: #fff;
        padding: 14px 20px;
        border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    }

    .toolbar-left {
        display: flex;
        gap: 10px;
        align-items: center;
    }

    .search-input { width: 260px; }
    .status-select { width: 120px; }
    .stat-tags { display: flex; gap: 6px; align-items: center; }

    /* 表格卡片 */
    .table-card { border-radius: 8px; }

    .price-label {
        font-size: 13px;
        color: #606266;
        margin-right: 4px;
    }

    .price-text {
        color: #409eff;
        font-weight: 600;
    }

    .price-text.lg {
        font-size: 24px;
    }

    .text-muted { color: #c0c4cc; }

    .pagination-bar {
        display: flex;
        justify-content: flex-end;
        padding: 16px 0 4px;
    }

    /* 详情弹窗 */
    .detail-body {
        display: flex;
        gap: 24px;
    }

    .detail-img-wrap {
        flex-shrink: 0;
    }

    .detail-image {
        width: 220px;
        height: 220px;
        border-radius: 8px;
        display: block;
    }

    .img-placeholder {
        width: 220px;
        height: 220px;
        background: #f5f7fa;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #c0c4cc;
        border-radius: 8px;
    }

    .detail-info {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 12px;
        overflow: hidden;
    }

    .detail-name {
        font-size: 18px;
        font-weight: 600;
        color: #303133;
    }

    .detail-price-row {
        display: flex;
        align-items: baseline;
        gap: 10px;
    }

    .detail-desc { margin-top: 2px; }

    /* 库存区块 */
    .stock-section {
        border-top: 1px solid #ebeef5;
        padding-top: 12px;
    }

    .stock-title {
        font-size: 14px;
        font-weight: 600;
        color: #606266;
        margin-bottom: 8px;
    }

    .stock-zero { color: #f56c6c; font-weight: 600; }
    .stock-normal { color: #67c23a; font-weight: 600; }
    .stock-empty { text-align: center; color: #909399; padding: 12px 0; font-size: 13px; }

    /* 表单 */
    .product-form { padding: 4px 8px; }

    .status-hint {
        margin-left: 10px;
        font-size: 12px;
        color: #e6a23c;
    }

    .upload-tip {
        font-size: 12px;
        color: #909399;
        margin-top: 4px;
    }
</style>
