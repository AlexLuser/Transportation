<template>
    <div class="warehouse-mgmt">
        <el-tabs v-model="activeTab" type="card" class="main-tabs">

            <!-- ==================== Tab1: 仓库管理 ==================== -->
            <el-tab-pane label="仓库管理" name="warehouses">
                <div class="toolbar">
                    <el-button type="primary" :icon="Plus" @click="openWarehouseDialog()">新建仓库</el-button>
                    <el-button :icon="Refresh" @click="loadWarehouses">刷新</el-button>
                </div>
                <el-table :data="warehouses" stripe v-loading="loadingWarehouses" class="data-table">
                    <el-table-column prop="id" label="ID" width="70" />
                    <el-table-column prop="warehouseName" label="仓库名称" min-width="160" />
                    <el-table-column label="地址" min-width="200" show-overflow-tooltip>
                        <template #default="{ row }">{{ row.city }} {{ row.district }} {{ row.detailAddress }}</template>
                    </el-table-column>
                    <el-table-column prop="capacity" label="容量(件)" width="100" align="center" />
                    <el-table-column label="归属Hub" width="140">
                        <template #default="{ row }">
                            <el-tag v-if="row.affiliatedHubId" type="primary" size="small">Hub #{{ row.affiliatedHubId }}</el-tag>
                            <span v-else class="text-muted">—</span>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="80" align="center">
                        <template #default="{ row }">
                            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                                {{ row.status === 1 ? '启用' : '禁用' }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="200" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="openWarehouseDialog(row)">编辑</el-button>
                            <el-button size="small" type="primary" plain @click="viewShops(row)">关联商家</el-button>
                            <el-button size="small" type="danger" plain @click="handleDeleteWarehouse(row.id)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab2: 库位管理 ==================== -->
            <el-tab-pane label="库位管理" name="locations">
                <div class="toolbar">
                    <el-select v-model="selectedWarehouseId" placeholder="选择仓库" style="width:200px" @change="loadLocations">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="primary" :icon="Plus" @click="openLocationDialog()" :disabled="!selectedWarehouseId">新增库位</el-button>
                    <el-button :icon="Refresh" @click="loadLocations" :disabled="!selectedWarehouseId">刷新</el-button>
                </div>
                <!-- 区域汇总卡片 -->
                <div class="zone-cards" v-if="zoneSummary.length">
                    <el-card v-for="z in zoneSummary" :key="z.zoneCode" class="zone-card" shadow="hover">
                        <div class="zone-title">{{ z.zoneCode }} 区</div>
                        <div class="zone-stat">库位数：<b>{{ z.totalLocations }}</b></div>
                        <div class="zone-stat">占用率：<b>{{ occupancyRate(z) }}%</b></div>
                        <el-progress :percentage="occupancyRate(z)" :color="progressColor(occupancyRate(z))" :show-text="false" />
                    </el-card>
                </div>
                <el-table :data="locations" stripe v-loading="loadingLocations" class="data-table">
                    <el-table-column prop="locationCode" label="库位编码" width="150" />
                    <el-table-column prop="zoneCode" label="区域" width="80" align="center" />
                    <el-table-column prop="rowNo" label="排" width="60" align="center" />
                    <el-table-column prop="shelfNo" label="架" width="60" align="center" />
                    <el-table-column prop="levelNo" label="层" width="60" align="center" />
                    <el-table-column label="占用/容量" width="120" align="center">
                        <template #default="{ row }">
                            <span :class="row.currentStock >= row.capacity ? 'text-danger' : 'text-success'">
                                {{ row.currentStock }}/{{ row.capacity }}
                            </span>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'danger'" size="small">
                                {{ ['禁用','正常','锁定'][row.status] }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="140" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="openLocationDialog(row)">编辑</el-button>
                            <el-button size="small" type="danger" plain @click="handleDeleteLocation(row.id)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab3: 入库管理 ==================== -->
            <el-tab-pane label="入库管理" name="inbound">
                <div class="toolbar">
                    <el-select v-model="inboundFilter.warehouseId" placeholder="筛选仓库" clearable style="width:160px">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-select v-model="inboundFilter.status" placeholder="筛选状态" clearable style="width:130px">
                        <el-option v-for="s in orderStatuses" :key="s.value" :label="s.label" :value="s.value" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadInboundOrders">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateInboundDialog">新建入库单</el-button>
                </div>
                <el-table :data="inboundOrders" stripe v-loading="loadingInbound" class="data-table">
                    <el-table-column prop="orderNo" label="入库单号" width="200" />
                    <el-table-column label="仓库" width="160">
                        <template #default="{ row }">{{ warehouseMap[row.warehouseId] ?? '仓库#' + row.warehouseId }}</template>
                    </el-table-column>
                    <el-table-column label="来源" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="sourceTypeMap[row.sourceType]?.type">{{ sourceTypeMap[row.sourceType]?.label ?? row.sourceType }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="statusTypeMap[row.status]?.type">{{ statusTypeMap[row.status]?.label ?? row.status }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="170" />
                    <el-table-column label="操作" width="200" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewOrderItems(row, 'inbound')">明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="handleStartInbound(row.id)">开始入库</el-button>
                            <el-button v-if="row.status === 'PROCESSING'" size="small" type="success" @click="handleCompleteInbound(row)">完成入库</el-button>
                            <el-button v-if="['PENDING','PROCESSING'].includes(row.status)" size="small" type="danger" plain @click="handleCancelOrder(row.id, 'inbound')">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab4: 出库管理 ==================== -->
            <el-tab-pane label="出库管理" name="outbound">
                <div class="toolbar">
                    <el-select v-model="outboundFilter.warehouseId" placeholder="筛选仓库" clearable style="width:160px">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-select v-model="outboundFilter.status" placeholder="筛选状态" clearable style="width:130px">
                        <el-option v-for="s in orderStatuses" :key="s.value" :label="s.label" :value="s.value" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadOutboundOrders">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateOutboundDialog">新建出库单</el-button>
                </div>
                <el-table :data="outboundOrders" stripe v-loading="loadingOutbound" class="data-table">
                    <el-table-column prop="orderNo" label="出库单号" width="200" />
                    <el-table-column label="仓库" width="160">
                        <template #default="{ row }">{{ warehouseMap[row.warehouseId] ?? '仓库#' + row.warehouseId }}</template>
                    </el-table-column>
                    <el-table-column label="目的类型" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="destTypeMap[row.destType]?.type">{{ destTypeMap[row.destType]?.label ?? row.destType }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="statusTypeMap[row.status]?.type">{{ statusTypeMap[row.status]?.label ?? row.status }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="170" />
                    <el-table-column label="操作" width="220" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewOrderItems(row, 'outbound')">明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="handleStartOutbound(row.id)">开始拣货</el-button>
                            <el-button v-if="row.status === 'PROCESSING'" size="small" type="success" @click="handleCompleteOutbound(row.id)">完成出库</el-button>
                            <el-button v-if="['PENDING','PROCESSING'].includes(row.status)" size="small" type="danger" plain @click="handleCancelOrder(row.id, 'outbound')">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab5: 盘点管理 ==================== -->
            <el-tab-pane label="盘点管理" name="check">
                <div class="toolbar">
                    <el-select v-model="checkFilter.warehouseId" placeholder="筛选仓库" clearable style="width:160px">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadInventoryChecks">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateCheckDialog">发起盘点</el-button>
                </div>
                <el-table :data="inventoryChecks" stripe v-loading="loadingCheck" class="data-table">
                    <el-table-column prop="checkNo" label="盘点单号" width="200" />
                    <el-table-column label="仓库" width="160">
                        <template #default="{ row }">{{ warehouseMap[row.warehouseId] ?? '仓库#' + row.warehouseId }}</template>
                    </el-table-column>
                    <el-table-column label="盘点类型" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small">{{ checkTypeMap[row.checkType] ?? row.checkType }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="zoneCode" label="盘点区域" width="100" align="center">
                        <template #default="{ row }">{{ row.zoneCode ?? '全仓' }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="checkStatusMap[row.status]?.type">{{ checkStatusMap[row.status]?.label ?? row.status }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="170" />
                    <el-table-column label="操作" width="220" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewCheckItems(row.id)">查看明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="handleStartCheck(row.id)">开始盘点</el-button>
                            <el-button v-if="row.status === 'PROCESSING'" size="small" type="warning" @click="viewCheckItems(row.id)">录入结果</el-button>
                            <el-button v-if="row.status === 'CONFIRMING'" size="small" type="success" @click="handleConfirmCheck(row.id)">确认调整</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab6: 调拨管理 ==================== -->
            <el-tab-pane label="调拨管理" name="transfer">
                <div class="toolbar">
                    <el-select v-model="transferFilter.status" placeholder="筛选状态" clearable style="width:130px">
                        <el-option label="待审批" value="PENDING" />
                        <el-option label="已审批" value="APPROVED" />
                        <el-option label="在途" value="IN_TRANSIT" />
                        <el-option label="已完成" value="DONE" />
                        <el-option label="已取消" value="CANCELLED" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadTransferOrders">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateTransferDialog">新建调拨单</el-button>
                </div>
                <el-table :data="transferOrders" stripe v-loading="loadingTransfer" class="data-table">
                    <el-table-column prop="orderNo" label="调拨单号" width="200" />
                    <el-table-column label="调出仓库" width="160">
                        <template #default="{ row }">{{ warehouseMap[row.srcWarehouseId] ?? '#' + row.srcWarehouseId }}</template>
                    </el-table-column>
                    <el-table-column label="调入仓库" width="160">
                        <template #default="{ row }">{{ warehouseMap[row.dstWarehouseId] ?? '#' + row.dstWarehouseId }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="transferStatusMap[row.status]?.type">{{ transferStatusMap[row.status]?.label ?? row.status }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="170" />
                    <el-table-column label="操作" width="240" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewOrderItems(row, 'transfer')">明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="handleApproveTransfer(row.id)">审批</el-button>
                            <el-button v-if="row.status === 'APPROVED'" size="small" type="success" @click="handleCompleteTransfer(row.id)">执行调拨</el-button>
                            <el-button v-if="['PENDING','APPROVED'].includes(row.status)" size="small" type="danger" plain @click="handleCancelTransfer(row.id)">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

        </el-tabs>

        <!-- ==================== 仓库编辑弹窗 ==================== -->
        <el-dialog v-model="warehouseDialogVisible" :title="warehouseForm.id ? '编辑仓库' : '新建仓库'" width="520px" align-center>
            <el-form :model="warehouseForm" label-width="100px">
                <el-form-item label="仓库名称"><el-input v-model="warehouseForm.warehouseName" /></el-form-item>
                <el-form-item label="联系电话"><el-input v-model="warehouseForm.warehousePhone" /></el-form-item>
                <el-form-item label="省份"><el-input v-model="warehouseForm.province" /></el-form-item>
                <el-form-item label="城市"><el-input v-model="warehouseForm.city" /></el-form-item>
                <el-form-item label="区/县"><el-input v-model="warehouseForm.district" /></el-form-item>
                <el-form-item label="详细地址"><el-input v-model="warehouseForm.detailAddress" /></el-form-item>
                <el-form-item label="容量(件)"><el-input-number v-model="warehouseForm.capacity" :min="0" style="width:100%" /></el-form-item>
                <el-form-item label="归属Hub ID"><el-input-number v-model="warehouseForm.affiliatedHubId" :min="1" style="width:100%" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="warehouseDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSaveWarehouse">保存</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 库位编辑弹窗 ==================== -->
        <el-dialog v-model="locationDialogVisible" :title="locationForm.id ? '编辑库位' : '新增库位'" width="460px" align-center>
            <el-form :model="locationForm" label-width="80px">
                <el-form-item label="区域编码"><el-input v-model="locationForm.zoneCode" placeholder="如 A、B、冷链" /></el-form-item>
                <el-form-item label="排号"><el-input v-model="locationForm.rowNo" /></el-form-item>
                <el-form-item label="架号"><el-input v-model="locationForm.shelfNo" /></el-form-item>
                <el-form-item label="层号"><el-input v-model="locationForm.levelNo" /></el-form-item>
                <el-form-item label="库位编码"><el-input v-model="locationForm.locationCode" placeholder="如 A-01-02-03" /></el-form-item>
                <el-form-item label="容量(件)"><el-input-number v-model="locationForm.capacity" :min="1" style="width:100%" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="locationDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSaveLocation">保存</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 新建入库单弹窗 ==================== -->
        <el-dialog v-model="createInboundVisible" title="新建入库单" width="560px" align-center>
            <el-form :model="inboundForm" label-width="100px">
                <el-form-item label="仓库">
                    <el-select v-model="inboundForm.warehouseId" placeholder="选择仓库" style="width:100%">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="来源类型">
                    <el-select v-model="inboundForm.sourceType" style="width:100%">
                        <el-option label="采购入库" value="PURCHASE" />
                        <el-option label="调拨入库" value="TRANSFER" />
                        <el-option label="退货入库" value="RETURN" />
                    </el-select>
                </el-form-item>
                <el-form-item label="商家">
                    <el-select v-model="inboundForm.shopId" filterable placeholder="选择商家" style="width:100%" @change="(val: number) => loadProductList(val)">
                        <el-option v-for="s in allShops" :key="s.id" :label="s.shopName" :value="s.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="inboundForm.remark" type="textarea" /></el-form-item>
                <el-divider>明细商品</el-divider>
                <div v-for="(item, idx) in inboundForm.items" :key="idx" class="item-row">
                    <el-select v-model="item.productId" filterable placeholder="搜索并选择商品"
                        @change="(val: number) => onPickProduct(val, item)" style="flex:1">
                        <el-option v-for="p in productList" :key="p.id" :label="p.productName" :value="p.id">
                            <span>{{ p.productName }}</span>
                            <span style="color:#909399;font-size:12px;margin-left:8px">¥{{ p.price ?? '' }}</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.expectedQty" :min="1" placeholder="数量" style="width:100px" />
                    <el-button :icon="Delete" circle type="danger" plain size="small" @click="inboundForm.items.splice(idx, 1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="inboundForm.items.push({ productId: null, productName: '', expectedQty: 1 })">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createInboundVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateInbound">提交</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 新建出库单弹窗 ==================== -->
        <el-dialog v-model="createOutboundVisible" title="新建出库单" width="560px" align-center>
            <el-form :model="outboundForm" label-width="100px">
                <el-form-item label="仓库">
                    <el-select v-model="outboundForm.warehouseId" placeholder="选择仓库" style="width:100%">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="目的类型">
                    <el-select v-model="outboundForm.destType" style="width:100%">
                        <el-option label="配送出库" value="DELIVERY" />
                        <el-option label="调拨出库" value="TRANSFER" />
                        <el-option label="退货出库" value="RETURN" />
                    </el-select>
                </el-form-item>
                <el-form-item label="商家">
                    <el-select v-model="outboundForm.shopId" filterable placeholder="选择商家" style="width:100%" @change="(val: number) => loadProductList(val)">
                        <el-option v-for="s in allShops" :key="s.id" :label="s.shopName" :value="s.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="关联订单ID"><el-input-number v-model="outboundForm.relatedId" style="width:100%" /></el-form-item>
                <el-form-item label="备注"><el-input v-model="outboundForm.remark" type="textarea" /></el-form-item>
                <el-divider>明细商品</el-divider>
                <div v-for="(item, idx) in outboundForm.items" :key="idx" class="item-row">
                    <el-select v-model="item.productId" filterable placeholder="搜索并选择商品"
                        @change="(val: number) => onPickProduct(val, item)" style="flex:1">
                        <el-option v-for="p in productList" :key="p.id" :label="p.productName" :value="p.id">
                            <span>{{ p.productName }}</span>
                            <span style="color:#909399;font-size:12px;margin-left:8px">¥{{ p.price ?? '' }}</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.quantity" :min="1" placeholder="数量" style="width:100px" />
                    <el-button :icon="Delete" circle type="danger" plain size="small" @click="outboundForm.items.splice(idx, 1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="outboundForm.items.push({ productId: null, productName: '', quantity: 1 })">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createOutboundVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateOutbound">提交</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 发起盘点弹窗 ==================== -->
        <el-dialog v-model="createCheckVisible" title="发起盘点" width="460px" align-center>
            <el-form :model="checkForm" label-width="100px">
                <el-form-item label="仓库">
                    <el-select v-model="checkForm.warehouseId" style="width:100%">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="盘点类型">
                    <el-select v-model="checkForm.checkType" style="width:100%">
                        <el-option label="全盘" value="FULL" />
                        <el-option label="分区盘" value="ZONE" />
                        <el-option label="动态盘" value="DYNAMIC" />
                    </el-select>
                </el-form-item>
                <el-form-item label="盘点区域" v-if="checkForm.checkType === 'ZONE'">
                    <el-input v-model="checkForm.zoneCode" placeholder="如 A、B" />
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="checkForm.remark" type="textarea" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="createCheckVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateCheck">发起</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 新建调拨单弹窗 ==================== -->
        <el-dialog v-model="createTransferVisible" title="新建调拨单" width="560px" align-center>
            <el-form :model="transferForm" label-width="100px">
                <el-form-item label="商家">
                    <el-select v-model="transferForm.shopId" filterable placeholder="选择商家" style="width:100%" @change="(val: number) => loadProductList(val)">
                        <el-option v-for="s in allShops" :key="s.id" :label="s.shopName" :value="s.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="调出仓库">
                    <el-select v-model="transferForm.srcWarehouseId" style="width:100%">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="调入仓库">
                    <el-select v-model="transferForm.dstWarehouseId" style="width:100%">
                        <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="transferForm.remark" type="textarea" /></el-form-item>
                <el-divider>调拨商品</el-divider>
                <div v-for="(item, idx) in transferForm.items" :key="idx" class="item-row">
                    <el-select v-model="item.productId" filterable placeholder="搜索并选择商品"
                        @change="(val: number) => onPickProduct(val, item)" style="flex:1">
                        <el-option v-for="p in productList" :key="p.id" :label="p.productName" :value="p.id">
                            <span>{{ p.productName }}</span>
                            <span style="color:#909399;font-size:12px;margin-left:8px">¥{{ p.price ?? '' }}</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.quantity" :min="1" placeholder="数量" style="width:100px" />
                    <el-button :icon="Delete" circle type="danger" plain size="small" @click="transferForm.items.splice(idx, 1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="transferForm.items.push({ productId: null, productName: '', quantity: 1 })">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createTransferVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateTransfer">提交</el-button>
            </template>
        </el-dialog>

        <!-- ==================== 明细查看弹窗 ==================== -->
        <el-dialog v-model="itemsDialogVisible" :title="itemsDialogTitle" width="600px">
            <el-table :data="currentItems" stripe size="small">
                <el-table-column prop="productId" label="商品ID" width="90" />
                <el-table-column prop="productName" label="商品名称" min-width="140" />
                <el-table-column v-if="itemsDialogType === 'inbound'" prop="expectedQty" label="预计数量" width="90" align="center" />
                <el-table-column v-if="itemsDialogType === 'inbound'" prop="actualQty" label="实际数量" width="90" align="center" />
                <el-table-column v-if="itemsDialogType !== 'inbound'" prop="quantity" label="数量" width="90" align="center" />
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.status === 'DONE' ? 'success' : 'info'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
            </el-table>
        </el-dialog>

        <!-- ==================== 盘点明细弹窗 ==================== -->
        <el-dialog v-model="checkItemsDialogVisible" title="盘点明细" width="700px">
            <el-table :data="checkItems" stripe size="small">
                <el-table-column prop="productId" label="商品ID" width="90" />
                <el-table-column prop="productName" label="商品名称" min-width="140" />
                <el-table-column prop="systemQty" label="系统数量" width="90" align="center" />
                <el-table-column prop="actualQty" label="实盘数量" width="90" align="center">
                    <template #default="{ row }">
                        <el-input-number v-if="currentCheckStatus === 'PROCESSING'" v-model="row.actualQty"
                            :min="0" size="small" style="width:80px"
                            @change="handleSubmitCheckItem(row)" />
                        <span v-else>{{ row.actualQty ?? '未盘' }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="差异" width="80" align="center">
                    <template #default="{ row }">
                        <span v-if="row.actualQty != null" :class="row.actualQty === row.systemQty ? 'text-success' : 'text-danger'">
                            {{ row.actualQty - row.systemQty > 0 ? '+' : '' }}{{ row.actualQty - row.systemQty }}
                        </span>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.status === 'DIFF' ? 'danger' : row.status === 'COUNTED' ? 'success' : 'info'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
            </el-table>
        </el-dialog>

        <!-- ==================== 仓库关联商家弹窗 ==================== -->
        <el-dialog v-model="shopsDialogVisible" :title="`${currentWarehouse?.warehouseName} - 关联商家`" width="500px">
            <el-table :data="warehouseShops" stripe size="small">
                <el-table-column prop="shopName" label="商家名称" min-width="140" />
                <el-table-column prop="shopPhone" label="联系电话" width="130" />
                <el-table-column prop="role" label="角色" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag :type="row.role === 'OWNER' ? 'warning' : 'info'" size="small">{{ row.role === 'OWNER' ? '所有者' : '租用方' }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                    <template #default="{ row }">
                        <el-button size="small" type="danger" plain @click="handleUnbindShop(row)">解绑</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div style="margin-top:16px;display:flex;align-items:center;gap:8px">
                <el-select v-model="bindShopId" filterable placeholder="选择商家" style="flex:1">
                    <el-option v-for="s in allShops" :key="s.id" :label="s.shopName" :value="s.id" />
                </el-select>
                <el-select v-model="bindShopRole" style="width:110px">
                    <el-option label="所有者" value="OWNER" />
                    <el-option label="租用方" value="TENANT" />
                </el-select>
                <el-button type="primary" @click="handleBindShop">绑定</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="WarehouseMgmt">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search, Delete } from '@element-plus/icons-vue';
import {
    getWarehouses, createWarehouse, updateWarehouse, deleteWarehouse,
    getLocationsByWarehouse, getZoneSummary, createLocation, updateLocation, deleteLocation,
    getInboundOrders, getInboundOrderItems, createInboundOrder, startInbound, completeInbound, cancelInbound,
    getOutboundOrders, getOutboundOrderItems, createOutboundOrder, startOutbound, completeOutbound, cancelOutbound,
    getInventoryChecks, getInventoryCheckItems, createInventoryCheck, startInventoryCheck, submitCheckItem, confirmInventoryCheck,
    getTransferOrders, getTransferOrderItems, createTransferOrder, approveTransfer, completeTransfer, cancelTransfer,
    getShopsByWarehouse, bindWarehouseShop, unbindWarehouseShop,
    getProducts, getAllShops
} from '@/api/shop';

// ==================== 全局状态 ====================
const activeTab = ref('warehouses');
const saving = ref(false);

// 仓库映射
const warehouses = ref<any[]>([]);
const warehouseMap = computed(() => {
    const m: Record<number, string> = {};
    warehouses.value.forEach(w => { if (w.id) m[w.id] = w.warehouseName; });
    return m;
});

const loadingWarehouses = ref(false);
const loadWarehouses = async () => {
    loadingWarehouses.value = true;
    try {
        const res = await getWarehouses();
        warehouses.value = res.data ?? [];
    } finally { loadingWarehouses.value = false; }
};

// 全部商家列表（管理员用）
const allShops = ref<any[]>([]);
const loadAllShops = async () => {
    try {
        const res = await getAllShops();
        allShops.value = res.data ?? [];
    } catch { /* 静默失败 */ }
};

// 商品列表（根据所选商家动态加载）
const productList = ref<any[]>([]);
const loadProductList = async (shopId?: number) => {
    productList.value = [];
    if (!shopId) return;
    try {
        const res = await getProducts({ shopId, size: 200, page: 1 });
        const raw = res.data;
        productList.value = raw?.records ?? raw ?? [];
    } catch { /* 静默失败 */ }
};
const onPickProduct = (productId: number, item: any) => {
    const p = productList.value.find((p: any) => p.id === productId);
    if (p) item.productName = p.productName;
};

// 状态映射
const orderStatuses = [
    { value: 'PENDING', label: '待处理' }, { value: 'PROCESSING', label: '处理中' },
    { value: 'DONE', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }
];
const statusTypeMap: Record<string, any> = {
    PENDING: { label: '待处理', type: 'info' }, PROCESSING: { label: '处理中', type: 'warning' },
    DONE: { label: '已完成', type: 'success' }, CANCELLED: { label: '已取消', type: 'danger' }
};
const sourceTypeMap: Record<string, any> = {
    PURCHASE: { label: '采购', type: '' }, TRANSFER: { label: '调拨', type: 'warning' }, RETURN: { label: '退货', type: 'danger' }
};
const destTypeMap: Record<string, any> = {
    DELIVERY: { label: '配送', type: 'primary' }, TRANSFER: { label: '调拨', type: 'warning' }, RETURN: { label: '退货', type: 'danger' }
};
const checkTypeMap: Record<string, string> = { FULL: '全盘', ZONE: '分区盘', DYNAMIC: '动态盘' };
const checkStatusMap: Record<string, any> = {
    PENDING: { label: '待盘点', type: 'info' }, PROCESSING: { label: '盘点中', type: 'warning' },
    CONFIRMING: { label: '待确认', type: 'primary' }, DONE: { label: '已完成', type: 'success' }
};
const transferStatusMap: Record<string, any> = {
    PENDING: { label: '待审批', type: 'info' }, APPROVED: { label: '已审批', type: 'primary' },
    IN_TRANSIT: { label: '在途', type: 'warning' }, DONE: { label: '已完成', type: 'success' }, CANCELLED: { label: '已取消', type: 'danger' }
};

// ==================== 仓库管理 ====================
const warehouseDialogVisible = ref(false);
const warehouseForm = reactive<any>({ warehouseName: '', warehousePhone: '', province: '', city: '', district: '', detailAddress: '', capacity: 0, affiliatedHubId: null });

const openWarehouseDialog = (w?: any) => {
    if (w) { Object.assign(warehouseForm, w); } else { Object.assign(warehouseForm, { id: undefined, warehouseName: '', warehousePhone: '', province: '', city: '', district: '', detailAddress: '', capacity: 0, affiliatedHubId: null }); }
    warehouseDialogVisible.value = true;
};
const handleSaveWarehouse = async () => {
    saving.value = true;
    try {
        if (warehouseForm.id) { await updateWarehouse(warehouseForm.id, warehouseForm); }
        else { await createWarehouse(warehouseForm); }
        ElMessage.success('保存成功');
        warehouseDialogVisible.value = false;
        loadWarehouses();
    } finally { saving.value = false; }
};
const handleDeleteWarehouse = async (id: number) => {
    await ElMessageBox.confirm('确认删除该仓库？', '警告', { type: 'warning' });
    await deleteWarehouse(id);
    ElMessage.success('已删除');
    loadWarehouses();
};

// ==================== 库位管理 ====================
const selectedWarehouseId = ref<number | null>(null);
const locations = ref<any[]>([]);
const zoneSummary = ref<any[]>([]);
const loadingLocations = ref(false);

const loadLocations = async () => {
    if (!selectedWarehouseId.value) return;
    loadingLocations.value = true;
    try {
        const [locRes, zoneRes] = await Promise.all([
            getLocationsByWarehouse(selectedWarehouseId.value),
            getZoneSummary(selectedWarehouseId.value)
        ]);
        locations.value = locRes.data ?? [];
        zoneSummary.value = zoneRes.data ?? [];
    } finally { loadingLocations.value = false; }
};

const occupancyRate = (z: any) => z.totalCapacity > 0 ? Math.round(z.totalStock / z.totalCapacity * 100) : 0;
const progressColor = (rate: number) => rate >= 90 ? '#f56c6c' : rate >= 70 ? '#e6a23c' : '#67c23a';

const locationDialogVisible = ref(false);
const locationForm = reactive<any>({ zoneCode: '', rowNo: '', shelfNo: '', levelNo: '', locationCode: '', capacity: 100 });
const openLocationDialog = (loc?: any) => {
    if (loc) { Object.assign(locationForm, loc); } else { Object.assign(locationForm, { id: undefined, zoneCode: '', rowNo: '', shelfNo: '', levelNo: '', locationCode: '', capacity: 100 }); }
    locationDialogVisible.value = true;
};
const handleSaveLocation = async () => {
    saving.value = true;
    try {
        if (locationForm.id) { await updateLocation(locationForm.id, { ...locationForm }); }
        else { await createLocation({ ...locationForm, warehouseId: selectedWarehouseId.value }); }
        ElMessage.success('保存成功');
        locationDialogVisible.value = false;
        loadLocations();
    } finally { saving.value = false; }
};
const handleDeleteLocation = async (id: number) => {
    await ElMessageBox.confirm('确认删除该库位？', '警告', { type: 'warning' });
    await deleteLocation(id);
    ElMessage.success('已删除');
    loadLocations();
};

// ==================== 入库管理 ====================
const inboundOrders = ref<any[]>([]);
const loadingInbound = ref(false);
const inboundFilter = reactive({ warehouseId: null as number | null, status: '' });
const createInboundVisible = ref(false);
const inboundForm = reactive<any>({ warehouseId: null, shopId: null, sourceType: 'PURCHASE', remark: '', items: [] });

const loadInboundOrders = async () => {
    loadingInbound.value = true;
    try {
        const res = await getInboundOrders({ warehouseId: inboundFilter.warehouseId || undefined, status: inboundFilter.status || undefined });
        inboundOrders.value = res.data ?? [];
    } finally { loadingInbound.value = false; }
};
const openCreateInboundDialog = () => {
    Object.assign(inboundForm, { warehouseId: null, shopId: null, sourceType: 'PURCHASE', remark: '', items: [] });
    productList.value = [];
    createInboundVisible.value = true;
};
const handleCreateInbound = async () => {
    if (!inboundForm.warehouseId) { ElMessage.warning('请选择仓库'); return; }
    if (!inboundForm.items.length || inboundForm.items.some((i: any) => !i.productId)) {
        ElMessage.warning('请至少添加一个商品明细，且每条明细都需选择商品'); return;
    }
    saving.value = true;
    try { await createInboundOrder({ ...inboundForm }); ElMessage.success('入库单已创建'); createInboundVisible.value = false; loadInboundOrders(); }
    finally { saving.value = false; }
};
const handleStartInbound = async (id: number) => { await startInbound(id); ElMessage.success('入库开始'); loadInboundOrders(); };
const handleCompleteInbound = async (row: any) => {
    const itemsRes = await getInboundOrderItems(row.id);
    await completeInbound(row.id, itemsRes.data ?? []);
    ElMessage.success('入库完成，库存已更新');
    loadInboundOrders();
};
const handleCancelOrder = async (id: number, type: string) => {
    await ElMessageBox.confirm('确认取消该单据？', '警告', { type: 'warning' });
    if (type === 'inbound') { await cancelInbound(id); loadInboundOrders(); }
    else if (type === 'outbound') { await cancelOutbound(id); loadOutboundOrders(); }
    ElMessage.success('已取消');
};

// ==================== 出库管理 ====================
const outboundOrders = ref<any[]>([]);
const loadingOutbound = ref(false);
const outboundFilter = reactive({ warehouseId: null as number | null, status: '' });
const createOutboundVisible = ref(false);
const outboundForm = reactive<any>({ warehouseId: null, shopId: null, destType: 'DELIVERY', relatedId: null, remark: '', items: [] });

const loadOutboundOrders = async () => {
    loadingOutbound.value = true;
    try {
        const res = await getOutboundOrders({ warehouseId: outboundFilter.warehouseId || undefined, status: outboundFilter.status || undefined });
        outboundOrders.value = res.data ?? [];
    } finally { loadingOutbound.value = false; }
};
const openCreateOutboundDialog = () => {
    Object.assign(outboundForm, { warehouseId: null, shopId: null, destType: 'DELIVERY', relatedId: null, remark: '', items: [] });
    productList.value = [];
    createOutboundVisible.value = true;
};
const handleCreateOutbound = async () => {
    if (!outboundForm.warehouseId) { ElMessage.warning('请选择仓库'); return; }
    if (!outboundForm.items.length || outboundForm.items.some((i: any) => !i.productId)) {
        ElMessage.warning('请至少添加一个商品明细，且每条明细都需选择商品'); return;
    }
    saving.value = true;
    try { await createOutboundOrder({ ...outboundForm }); ElMessage.success('出库单已创建'); createOutboundVisible.value = false; loadOutboundOrders(); }
    finally { saving.value = false; }
};
const handleStartOutbound = async (id: number) => { await startOutbound(id); ElMessage.success('拣货开始'); loadOutboundOrders(); };
const handleCompleteOutbound = async (id: number) => { await completeOutbound(id); ElMessage.success('出库完成，库存已扣减'); loadOutboundOrders(); };

// ==================== 盘点管理 ====================
const inventoryChecks = ref<any[]>([]);
const loadingCheck = ref(false);
const checkFilter = reactive({ warehouseId: null as number | null });
const createCheckVisible = ref(false);
const checkForm = reactive<any>({ warehouseId: null, checkType: 'FULL', zoneCode: '', remark: '' });
const checkItemsDialogVisible = ref(false);
const checkItems = ref<any[]>([]);
const currentCheckId = ref<number | null>(null);
const currentCheckStatus = ref('');

const loadInventoryChecks = async () => {
    loadingCheck.value = true;
    try {
        const res = await getInventoryChecks({ warehouseId: checkFilter.warehouseId || undefined });
        inventoryChecks.value = res.data ?? [];
    } finally { loadingCheck.value = false; }
};
const openCreateCheckDialog = () => { Object.assign(checkForm, { warehouseId: null, checkType: 'FULL', zoneCode: '', remark: '' }); createCheckVisible.value = true; };
const handleCreateCheck = async () => {
    saving.value = true;
    try { await createInventoryCheck({ ...checkForm }); ElMessage.success('盘点单已发起'); createCheckVisible.value = false; loadInventoryChecks(); }
    finally { saving.value = false; }
};
const handleStartCheck = async (id: number) => { await startInventoryCheck(id); ElMessage.success('盘点已开始'); loadInventoryChecks(); };
const viewCheckItems = async (id: number) => {
    currentCheckId.value = id;
    const check = inventoryChecks.value.find(c => c.id === id);
    currentCheckStatus.value = check?.status ?? '';
    const res = await getInventoryCheckItems(id);
    checkItems.value = res.data ?? [];
    checkItemsDialogVisible.value = true;
};
const handleSubmitCheckItem = async (item: any) => {
    if (currentCheckId.value && item.id && item.actualQty != null) {
        await submitCheckItem(currentCheckId.value, item.id, item.actualQty);
    }
};
const handleConfirmCheck = async (id: number) => {
    await ElMessageBox.confirm('确认盘点结果并调整库存差异？', '提示', { type: 'warning' });
    await confirmInventoryCheck(id);
    ElMessage.success('盘点确认完成，库存已调整');
    loadInventoryChecks();
};

// ==================== 调拨管理 ====================
const transferOrders = ref<any[]>([]);
const loadingTransfer = ref(false);
const transferFilter = reactive({ status: '' });
const createTransferVisible = ref(false);
const transferForm = reactive<any>({ shopId: null, srcWarehouseId: null, dstWarehouseId: null, remark: '', items: [] });

const loadTransferOrders = async () => {
    loadingTransfer.value = true;
    try {
        const res = await getTransferOrders({ status: transferFilter.status || undefined });
        transferOrders.value = res.data ?? [];
    } finally { loadingTransfer.value = false; }
};
const openCreateTransferDialog = () => {
    Object.assign(transferForm, { shopId: null, srcWarehouseId: null, dstWarehouseId: null, remark: '', items: [] });
    productList.value = [];
    createTransferVisible.value = true;
};
const handleCreateTransfer = async () => {
    if (!transferForm.srcWarehouseId) { ElMessage.warning('请选择调出仓库'); return; }
    if (!transferForm.dstWarehouseId) { ElMessage.warning('请选择调入仓库'); return; }
    if (transferForm.srcWarehouseId === transferForm.dstWarehouseId) {
        ElMessage.warning('调出仓库和调入仓库不能相同'); return;
    }
    if (!transferForm.items.length || transferForm.items.some((i: any) => !i.productId)) {
        ElMessage.warning('请至少添加一个调拨商品，且每条明细都需选择商品'); return;
    }
    saving.value = true;
    try { await createTransferOrder({ ...transferForm }); ElMessage.success('调拨单已创建'); createTransferVisible.value = false; loadTransferOrders(); }
    finally { saving.value = false; }
};
const handleApproveTransfer = async (id: number) => { await approveTransfer(id); ElMessage.success('已审批'); loadTransferOrders(); };
const handleCompleteTransfer = async (id: number) => { await completeTransfer(id); ElMessage.success('调拨完成，库存已转移'); loadTransferOrders(); };
const handleCancelTransfer = async (id: number) => {
    await ElMessageBox.confirm('确认取消调拨？', '警告', { type: 'warning' });
    await cancelTransfer(id);
    ElMessage.success('已取消');
    loadTransferOrders();
};

// ==================== 明细查看 ====================
const itemsDialogVisible = ref(false);
const itemsDialogTitle = ref('');
const itemsDialogType = ref('');
const currentItems = ref<any[]>([]);

const viewOrderItems = async (row: any, type: string) => {
    itemsDialogType.value = type;
    itemsDialogTitle.value = `${type === 'inbound' ? '入库' : type === 'outbound' ? '出库' : '调拨'}单明细 - ${row.orderNo}`;
    let res: any;
    if (type === 'inbound') res = await getInboundOrderItems(row.id);
    else if (type === 'outbound') res = await getOutboundOrderItems(row.id);
    else res = await getTransferOrderItems(row.id);
    currentItems.value = res.data ?? [];
    itemsDialogVisible.value = true;
};

// ==================== 共享仓管理 ====================
const shopsDialogVisible = ref(false);
const warehouseShops = ref<any[]>([]);
const currentWarehouse = ref<any>(null);
const bindShopId = ref<number | null>(null);
const bindShopRole = ref('TENANT');

const viewShops = async (warehouse: any) => {
    currentWarehouse.value = warehouse;
    const res = await getShopsByWarehouse(warehouse.id);
    warehouseShops.value = res.data ?? [];
    shopsDialogVisible.value = true;
};
const handleBindShop = async () => {
    if (!bindShopId.value || !currentWarehouse.value) return;
    await bindWarehouseShop(currentWarehouse.value.id, bindShopId.value, bindShopRole.value);
    ElMessage.success('绑定成功');
    const res = await getShopsByWarehouse(currentWarehouse.value.id);
    warehouseShops.value = res.data ?? [];
    bindShopId.value = null;
};
const handleUnbindShop = async (shop: any) => {
    await ElMessageBox.confirm(`确认解绑商家"${shop.shopName}"？`, '警告', { type: 'warning' });
    await unbindWarehouseShop(currentWarehouse.value.id, shop.id);
    ElMessage.success('已解绑');
    const res = await getShopsByWarehouse(currentWarehouse.value.id);
    warehouseShops.value = res.data ?? [];
};

onMounted(() => {
    loadWarehouses();
    loadAllShops();
    loadInboundOrders();
    loadOutboundOrders();
    loadInventoryChecks();
    loadTransferOrders();
});
</script>

<style scoped>
.warehouse-mgmt { display: flex; flex-direction: column; gap: 0; }
.main-tabs { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.data-table { width: 100%; }

.zone-cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.zone-card { width: 160px; text-align: center; }
.zone-title { font-size: 18px; font-weight: bold; color: #409eff; margin-bottom: 6px; }
.zone-stat { font-size: 12px; color: #606266; margin-bottom: 4px; }

.item-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }

.text-muted { color: #c0c4cc; }
.text-success { color: #67c23a; font-weight: 600; }
.text-danger { color: #f56c6c; font-weight: 600; }
</style>
