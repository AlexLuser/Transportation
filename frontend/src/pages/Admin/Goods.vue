<template>
    <div class="admin-goods">

        <div class="toolbar">
            <div class="toolbar-left">
                <el-input v-model="keyword" placeholder="搜索承运物名称" clearable class="search-input"
                    @keyup.enter="handleSearch" @clear="handleSearch" />
                <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
                    <el-option label="启用" :value="1" />
                    <el-option label="停用" :value="0" />
                    <el-option label="待审核" :value="2" />
                </el-select>
                <el-select v-model="sortType" class="sort-select" @change="handleSearch">
                    <el-option label="最新创建" value="createTime_desc" />
                    <el-option label="价格最低" value="price_asc" />
                    <el-option label="发件数最高" value="salesCount_desc" />
                </el-select>
                <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            </div>
            <span class="result-hint">共 {{ total }} 件承运物</span>
        </div>

        <el-card shadow="never" class="table-card" v-loading="loading">
            <el-table :data="products" stripe>
                <el-table-column label="承运物名称" prop="productName" min-width="160" show-overflow-tooltip />
                <el-table-column label="承运物编码" prop="productCode" width="130" show-overflow-tooltip />
                <el-table-column label="所属商户" prop="shopId" width="90" align="center" />
                <el-table-column label="申报价值" width="100">
                    <template #default="{ row }"><span class="price-text">¥{{ row.price }}</span></template>
                </el-table-column>
                <el-table-column label="参考价值" width="100">
                    <template #default="{ row }">
                        <span class="original-price" v-if="row.originalPrice">¥{{ row.originalPrice }}</span>
                        <span class="text-muted" v-else>-</span>
                    </template>
                </el-table-column>
                <el-table-column label="单位" prop="unit" width="80" />
                <el-table-column label="已发件数" prop="salesCount" width="90" align="center" />
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="statusTagType(row.status)" size="small">
                            {{ statusLabel(row.status) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="160" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" @click="openDetail(row)">详情</el-button>
                        <el-button
                            v-if="row.status === 2"
                            size="small"
                            type="success"
                            @click="handleApprove(row)"
                        >通过审核</el-button>
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

        <!-- 承运物详情弹窗 -->
        <el-dialog v-model="detailVisible" :title="detailProduct?.productName" width="700px" align-center>
            <div v-if="detailProduct" class="detail-body">
                <div class="detail-img-wrap">
                    <el-image :src="getFirstImage(detailProduct.images)" fit="cover" class="detail-image">
                        <template #error>
                            <div class="img-placeholder"><el-icon size="48"><Picture /></el-icon></div>
                        </template>
                    </el-image>
                </div>
                <div class="detail-info">
                    <div class="detail-price-row">
                        <span class="price-text lg">¥{{ detailProduct.price }}</span>
                        <span class="original-price" v-if="detailProduct.originalPrice && detailProduct.originalPrice > detailProduct.price">
                            原价 ¥{{ detailProduct.originalPrice }}
                        </span>
                    </div>
                    <el-descriptions :column="2" border size="small">
                        <el-descriptions-item label="所属商户">{{ detailProduct.shopId }}</el-descriptions-item>
                        <el-descriptions-item label="承运物编码">{{ detailProduct.productCode ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="承运物分类">{{ detailProduct.categoryId ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="单位">{{ detailProduct.unit ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="重量">{{ detailProduct.weight ? detailProduct.weight + ' kg' : '-' }}</el-descriptions-item>
                        <el-descriptions-item label="已发件数">{{ detailProduct.salesCount ?? 0 }} 件</el-descriptions-item>
                        <el-descriptions-item label="状态">
                            <el-tag :type="statusTagType(detailProduct.status)" size="small">
                                {{ statusLabel(detailProduct.status) }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="创建时间">{{ formatDate(detailProduct.createTime) }}</el-descriptions-item>
                        <el-descriptions-item label="承运物描述" :span="2">{{ detailProduct.description || '-' }}</el-descriptions-item>
                    </el-descriptions>
                </div>
            </div>
            <template #footer>
                <el-button
                    v-if="detailProduct && detailProduct.status === 2"
                    type="success"
                    :loading="approving"
                    @click="handleApprove(detailProduct)"
                >通过审核</el-button>
                <el-button @click="detailVisible = false">关闭</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="AdminGoods">
    import { ref, onMounted } from 'vue';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import { Search, Picture } from '@element-plus/icons-vue';
    import { adminSearchProducts, adminApproveProduct } from '@/api/mall';

    const loading = ref(false);
    const products = ref<any[]>([]);
    const keyword = ref('');
    const sortType = ref('createTime_desc');
    const statusFilter = ref<number | null>(null);
    const currentPage = ref(1);
    const pageSize = ref(10);
    const total = ref(0);

    const fetchProducts = async () => {
        loading.value = true;
        const [sortField, sortOrder] = sortType.value.split('_');
        try {
            const params: any = {
                current: currentPage.value,
                size: pageSize.value,
                sortField,
                sortOrder,
            };
            if (keyword.value) params.keyword = keyword.value;
            const res = await adminSearchProducts(params);
            let records: any[] = res.data?.records ?? [];
            if (statusFilter.value !== null) {
                records = records.filter((p: any) => p.status === statusFilter.value);
            }
            products.value = records;
            total.value = statusFilter.value !== null ? records.length : (res.data?.total ?? 0);
        } finally {
            loading.value = false;
        }
    };

    const handleSearch = () => { currentPage.value = 1; fetchProducts(); };

    const detailVisible = ref(false);
    const detailProduct = ref<any>(null);
    const approving = ref(false);

    const openDetail = (row: any) => { detailProduct.value = { ...row }; detailVisible.value = true; };

    const handleApprove = async (row: any) => {
        await ElMessageBox.confirm(`确认将承运物「${row.productName}」审核通过（启用）吗？`, '审核确认', { type: 'warning' });
        approving.value = true;
        try {
            await adminApproveProduct(row.id, row);
            ElMessage.success('审核通过，承运物档案已启用');
            detailVisible.value = false;
            fetchProducts();
        } catch {
            ElMessage.error('审核操作失败，请稍后重试或联系系统管理员');
        } finally {
            approving.value = false;
        }
    };

    const statusLabel = (s: number) => s === 1 ? '启用' : s === 0 ? '停用' : s === 2 ? '待审核' : '-';
    const statusTagType = (s: number) => s === 1 ? 'success' : s === 0 ? 'info' : 'warning';

    const getFirstImage = (images: string) => {
        try { const a = JSON.parse(images); return Array.isArray(a) && a.length > 0 ? a[0] : ''; } catch { return ''; }
    };
    const formatDate = (d: string | undefined) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    onMounted(() => fetchProducts());
</script>

<style scoped>
    .admin-goods { display: flex; flex-direction: column; gap: 16px; }
    .toolbar { display: flex; align-items: center; justify-content: space-between; background: #fff; padding: 14px 20px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
    .toolbar-left { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .search-input { width: 220px; }
    .status-select { width: 110px; }
    .sort-select { width: 130px; }
    .result-hint { font-size: 13px; color: #606266; white-space: nowrap; }
    .table-card { border-radius: 8px; }
    .price-text { color: #f56c6c; font-weight: 500; }
    .price-text.lg { font-size: 22px; font-weight: 700; }
    .original-price { color: #909399; text-decoration: line-through; font-size: 13px; }
    .text-muted { color: #c0c4cc; }
    .pagination-bar { display: flex; justify-content: flex-end; padding: 16px 0 4px; }

    .detail-body { display: flex; gap: 20px; }
    .detail-img-wrap { flex-shrink: 0; }
    .detail-image { width: 200px; height: 200px; border-radius: 8px; display: block; }
    .img-placeholder { width: 200px; height: 200px; background: #f5f7fa; display: flex; align-items: center; justify-content: center; color: #c0c4cc; border-radius: 8px; }
    .detail-info { flex: 1; display: flex; flex-direction: column; gap: 12px; }
    .detail-price-row { display: flex; align-items: baseline; gap: 10px; }
</style>
