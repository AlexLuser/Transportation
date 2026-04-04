<template>
    <div class="stock-manage">

        <!-- 顶部操作栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <el-input
                    v-model="keyword"
                    placeholder="搜索商品名称 / 编码"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                    @clear="handleSearch"
                />
                <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            </div>
            <el-button :icon="Refresh" @click="init">刷新</el-button>
        </div>

        <!-- 商品库存表格（可展开查看各仓库明细） -->
        <el-card shadow="never" class="table-card" v-loading="loading">
            <el-table
                :data="filteredProducts"
                row-key="id"
                stripe
                @expand-change="handleExpand"
            >
                <!-- 展开列 -->
                <el-table-column type="expand">
                    <template #default="{ row }">
                        <div class="expand-panel">
                            <div class="expand-title">各仓库库存明细</div>
                            <el-table
                                :data="stockMap[row.id] ?? []"
                                v-loading="expandLoading[row.id]"
                                border
                                size="small"
                                class="warehouse-table"
                            >
                                <el-table-column label="仓库名称" min-width="160">
                                    <template #default="{ row: s }">
                                        {{ warehouseMap[s.warehouseId] ?? ('仓库 #' + s.warehouseId) }}
                                    </template>
                                </el-table-column>
                                <el-table-column label="库存数量" width="120" align="center">
                                    <template #default="{ row: s }">
                                        <span :class="s.stock === 0 ? 'stock-zero' : 'stock-normal'">{{ s.stock }}</span>
                                    </template>
                                </el-table-column>
                                <el-table-column label="操作" width="100" align="center">
                                    <template #default="{ row: s }">
                                        <el-button size="small" type="primary" plain @click="openEditStock(row, s)">调整</el-button>
                                    </template>
                                </el-table-column>
                            </el-table>

                            <!-- 在任意仓库新增该商品库存 -->
                            <div class="add-stock-row">
                                <el-button
                                    size="small"
                                    :icon="Plus"
                                    @click="openAddStock(row)"
                                >新增仓库库存</el-button>
                            </div>
                        </div>
                    </template>
                </el-table-column>

                <el-table-column label="商品名称" prop="productName" min-width="160" show-overflow-tooltip />
                <el-table-column label="商品编码" prop="productCode" width="130" show-overflow-tooltip />
                <el-table-column label="售价" width="100">
                    <template #default="{ row }">
                        <span class="price-text">¥{{ row.price }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="单位" prop="unit" width="80" />
                <el-table-column label="总库存" width="100" align="center">
                    <template #default="{ row }">
                        <el-tag :type="totalStock(row.id) === 0 ? 'danger' : 'success'" size="small">
                            {{ stockMap[row.id] ? totalStock(row.id) : '—' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'warning'" size="small">
                            {{ row.status === 1 ? '上架' : row.status === 0 ? '下架' : '待审核' }}
                        </el-tag>
                    </template>
                </el-table-column>
            </el-table>

            <el-empty v-if="!loading && filteredProducts.length === 0" description="暂无商品" />
        </el-card>

        <!-- ===================== 调整库存弹窗 ===================== -->
        <el-dialog
            v-model="stockDialogVisible"
            :title="isAddWarehouse ? '新增仓库库存' : '调整库存'"
            width="440px"
            align-center
        >
            <el-form label-width="90px" class="stock-form">
                <el-form-item label="商品">
                    <el-text>{{ currentProduct?.productName }}</el-text>
                </el-form-item>
                <el-form-item label="仓库" v-if="!isAddWarehouse">
                    <el-text>{{ warehouseMap[currentStock?.warehouseId] ?? ('仓库 #' + currentStock?.warehouseId) }}</el-text>
                </el-form-item>
                <el-form-item label="选择仓库" v-if="isAddWarehouse">
                    <el-select v-model="newWarehouseId" placeholder="请选择仓库" style="width:100%">
                        <el-option
                            v-for="w in availableWarehouses"
                            :key="w.id"
                            :label="w.warehouseName"
                            :value="w.id"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item label="库存数量">
                    <el-input-number
                        v-model="newStockValue"
                        :min="0"
                        :precision="0"
                        style="width:100%"
                        controls-position="right"
                    />
                </el-form-item>
                <div v-if="!isAddWarehouse" class="stock-hint">
                    当前库存：<span :class="currentStock?.stock === 0 ? 'stock-zero' : 'stock-normal'">{{ currentStock?.stock ?? 0 }}</span>
                </div>
            </el-form>
            <template #footer>
                <el-button @click="stockDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="stockSubmitting" @click="handleStockSubmit">确认</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="ShopStock">
    import { ref, computed, onMounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { Search, Refresh, Plus } from '@element-plus/icons-vue';
    import { getProducts, getWarehouses, getStockByProduct, updateStock, getMyShop, type Product, type Warehouse } from '@/api/shop';
    import { useUserStore } from '@/stores/userStore';

    const userStore = useUserStore();

    // ==================== 基础数据 ====================
    const loading = ref(false);
    const products = ref<Product[]>([]);
    const allWarehouses = ref<Warehouse[]>([]);
    const warehouseMap = ref<Record<number, string>>({});

    // productId → 库存列表
    const stockMap = ref<Record<number, any[]>>({});
    // productId → 展开加载中
    const expandLoading = ref<Record<number, boolean>>({});

    const keyword = ref('');

    const filteredProducts = computed(() => {
        if (!keyword.value) return products.value;
        const kw = keyword.value.toLowerCase();
        return products.value.filter(p =>
            p.productName?.toLowerCase().includes(kw) ||
            p.productCode?.toLowerCase().includes(kw)
        );
    });

    const totalStock = (productId: number | undefined) => {
        if (!productId) return 0;
        return (stockMap.value[productId] ?? []).reduce((sum: number, s: any) => sum + (s.stock ?? 0), 0);
    };

    const handleSearch = () => { /* filteredProducts 是计算属性 */ };

    // ==================== 初始化 ====================
    const init = async () => {
        loading.value = true;
        try {
            const [prodRes, whRes] = await Promise.all([getProducts({}), getWarehouses()]);
            products.value = prodRes.data?.records ?? [];
            allWarehouses.value = whRes.data ?? [];
            allWarehouses.value.forEach((w: Warehouse) => {
                if (w.id) warehouseMap.value[w.id] = w.warehouseName;
            });
        } finally {
            loading.value = false;
        }
    };

    // ==================== 展开行：懒加载库存 ====================
    const handleExpand = async (row: Product, expandedRows: Product[]) => {
        const id = row.id as number;
        const isExpanded = expandedRows.some(r => r.id === id);
        if (!isExpanded || stockMap.value[id] !== undefined) return;

        expandLoading.value[id] = true;
        try {
            const res = await getStockByProduct(id);
            stockMap.value[id] = res.data ?? [];
        } finally {
            expandLoading.value[id] = false;
        }
    };

    // ==================== 调整库存弹窗 ====================
    const stockDialogVisible = ref(false);
    const stockSubmitting = ref(false);
    const isAddWarehouse = ref(false);

    const currentProduct = ref<Product | null>(null);
    const currentStock = ref<any>(null);
    const newStockValue = ref(0);
    const newWarehouseId = ref<number | null>(null);

    // 当前商品尚未配置库存的仓库列表
    const availableWarehouses = computed(() => {
        if (!currentProduct.value?.id) return allWarehouses.value;
        const used = new Set((stockMap.value[currentProduct.value.id as number] ?? []).map((s: any) => s.warehouseId));
        return allWarehouses.value.filter(w => !used.has(w.id));
    });

    const openEditStock = (product: Product, stock: any) => {
        currentProduct.value = product;
        currentStock.value = stock;
        newStockValue.value = stock.stock ?? 0;
        isAddWarehouse.value = false;
        stockDialogVisible.value = true;
    };

    const openAddStock = (product: Product) => {
        currentProduct.value = product;
        currentStock.value = null;
        newStockValue.value = 0;
        newWarehouseId.value = null;
        isAddWarehouse.value = true;
        stockDialogVisible.value = true;
    };

    const handleStockSubmit = async () => {
        const productId = currentProduct.value?.id as number;
        const warehouseId = isAddWarehouse.value ? newWarehouseId.value : currentStock.value?.warehouseId;

        if (!warehouseId) {
            ElMessage.warning('请选择仓库');
            return;
        }

        stockSubmitting.value = true;
        try {
            await updateStock({ warehouseId, productId, stock: newStockValue.value });
            ElMessage.success('库存更新成功');
            stockDialogVisible.value = false;

            // 刷新该商品的库存数据
            const res = await getStockByProduct(productId);
            stockMap.value[productId] = res.data ?? [];
        } finally {
            stockSubmitting.value = false;
        }
    };

    onMounted(() => {
        init();
    });
</script>

<style scoped>
    .stock-manage {
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

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
    .table-card { border-radius: 8px; }
    .price-text { color: #f56c6c; font-weight: 500; }

    /* 展开面板 */
    .expand-panel {
        padding: 12px 40px 12px 60px;
        background: #fafafa;
    }

    .expand-title {
        font-size: 13px;
        font-weight: 600;
        color: #606266;
        margin-bottom: 10px;
        padding-left: 8px;
        border-left: 3px solid #409eff;
    }

    .warehouse-table {
        max-width: 500px;
    }

    .add-stock-row {
        margin-top: 10px;
    }

    .stock-zero { color: #f56c6c; font-weight: 700; }
    .stock-normal { color: #67c23a; font-weight: 700; }

    /* 调整弹窗 */
    .stock-form { padding: 8px 0; }

    .stock-hint {
        font-size: 13px;
        color: #606266;
        padding-left: 90px;
        margin-top: -8px;
        margin-bottom: 4px;
    }
</style>
