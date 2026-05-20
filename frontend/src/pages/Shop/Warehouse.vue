<template>
    <div class="shop-warehouse">
        <el-tabs v-model="activeTab" type="card" class="main-tabs">

            <!-- ==================== Tab1: 我的仓库 ==================== -->
            <el-tab-pane label="我的仓库" name="my-warehouses">
                <el-table :data="myWarehouses" stripe v-loading="loadingWarehouses">
                    <el-table-column prop="warehouseName" label="仓库名称" min-width="160" />
                    <el-table-column label="地址" min-width="200" show-overflow-tooltip>
                        <template #default="{ row }">{{ row.city }} {{ row.district }} {{ row.detailAddress }}</template>
                    </el-table-column>
                    <el-table-column prop="capacity" label="容量(件)" width="100" align="center" />
                    <el-table-column label="角色" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag :type="row.role === 'OWNER' ? 'warning' : 'info'" size="small">
                                {{ row.role === 'OWNER' ? '所有者' : '租用方' }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="140" align="center">
                        <template #default="{ row }">
                            <el-button size="small" type="primary" plain @click="goToLocations(row)">查看库位</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab2: 库位管理 ==================== -->
            <el-tab-pane label="库位管理" name="locations">
                <div class="toolbar">
                    <el-select v-model="locWarehouseId" placeholder="选择仓库" style="width:200px" @change="loadLocations">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="success" :icon="Plus" :disabled="!locWarehouseId" @click="openLocationDialog()">添加库位</el-button>
                    <el-button :icon="Refresh" @click="loadLocations">刷新</el-button>
                </div>
                <el-table :data="locations" stripe v-loading="loadingLocations">
                    <el-table-column prop="locationCode" label="库位编码" width="140" />
                    <el-table-column prop="zoneCode" label="区域" width="100" align="center" />
                    <el-table-column prop="rowNo" label="排" width="70" align="center" />
                    <el-table-column prop="shelfNo" label="架" width="70" align="center" />
                    <el-table-column prop="levelNo" label="层" width="70" align="center" />
                    <el-table-column prop="capacity" label="容量" width="80" align="center" />
                    <el-table-column prop="currentStock" label="当前占用" width="90" align="center" />
                    <el-table-column label="状态" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'">
                                {{ ['禁用','正常','锁定'][row.status] ?? row.status }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="120" align="center">
                        <template #default="{ row }">
                            <el-button size="small" type="primary" plain @click="openLocationDialog(row)">编辑</el-button>
                            <el-button size="small" type="danger" plain @click="handleDeleteLocation(row.id)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab3: 入库管理 ==================== -->
            <el-tab-pane label="入库管理" name="inbound">
                <div class="toolbar">
                    <el-select v-model="inboundFilter.warehouseId" placeholder="筛选仓库" clearable style="width:160px">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadInboundOrders">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateInbound">新建入库单</el-button>
                </div>
                <el-table :data="inboundOrders" stripe v-loading="loadingInbound">
                    <el-table-column prop="orderNo" label="入库单号" width="200" />
                    <el-table-column label="仓库" width="150">
                        <template #default="{ row }">{{ warehouseMap[row.warehouseId] }}</template>
                    </el-table-column>
                    <el-table-column label="来源" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag size="small">{{ ({ PURCHASE:'采购', TRANSFER:'调拨', RETURN:'退货' } as any)[row.sourceType] ?? row.sourceType }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="statusTypeMap[row.status]">{{ statusLabelMap[row.status] }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="160" />
                    <el-table-column label="操作" width="220" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewItems(row.id, 'inbound', row.orderNo)">明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary"
                                @click="handleStartInbound(row.id)">开始入库</el-button>
                            <el-button v-if="row.status === 'PROCESSING'" size="small" type="success"
                                @click="openCompleteInbound(row)">完成入库</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="danger" plain
                                @click="handleCancelInbound(row.id)">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab4: 出库管理 ==================== -->
            <el-tab-pane label="出库管理" name="outbound">
                <div class="toolbar">
                    <el-select v-model="outboundFilter.warehouseId" placeholder="筛选仓库" clearable style="width:160px">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="primary" :icon="Search" @click="loadOutboundOrders">查询</el-button>
                    <el-button type="success" :icon="Plus" @click="openCreateOutbound">新建出库单</el-button>
                </div>
                <el-table :data="outboundOrders" stripe v-loading="loadingOutbound">
                    <el-table-column prop="orderNo" label="出库单号" width="200" />
                    <el-table-column label="仓库" width="150">
                        <template #default="{ row }">{{ warehouseMap[row.warehouseId] }}</template>
                    </el-table-column>
                    <el-table-column label="目的类型" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" type="primary">{{ ({ DELIVERY:'配送', TRANSFER:'调拨', RETURN:'退货' } as any)[row.destType] ?? row.destType }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="90" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="statusTypeMap[row.status]">{{ statusLabelMap[row.status] }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="160" />
                    <el-table-column label="操作" width="260" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewItems(row.id, 'outbound', row.orderNo)">明细</el-button>
                            <el-button v-if="row.status === 'PENDING'" size="small" type="primary"
                                @click="handleStartOutbound(row.id)">开始拣货</el-button>
                            <el-button v-if="row.status === 'PROCESSING'" size="small" type="success"
                                @click="handleCompleteOutbound(row.id)">完成出库</el-button>
                            <el-button v-if="['PENDING','PROCESSING'].includes(row.status)" size="small" type="danger" plain
                                @click="handleCancelOutbound(row.id)">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab5: 库存 ==================== -->
            <el-tab-pane label="库存" name="stock">
                <div class="toolbar">
                    <el-select v-model="stockWarehouseId" placeholder="选择仓库" clearable style="width:200px" @change="loadMyStock">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                    <el-button type="primary" :icon="Refresh" @click="loadMyStock">刷新</el-button>
                </div>
                <el-table :data="myStockList" stripe v-loading="loadingStock" empty-text="请选择仓库查看当前商家库存">
                    <el-table-column prop="productId" label="商品ID" width="90" />
                    <el-table-column prop="productName" label="商品名称" min-width="160" />
                    <el-table-column prop="stock" label="库存" width="100" align="center" />
                    <el-table-column prop="unit" label="单位" width="80" align="center" />
                    <el-table-column label="操作" width="120" align="center">
                        <template #default="{ row }">
                            <el-button size="small" type="primary" plain :disabled="!stockWarehouseId"
                                @click="openStockEdit(row)">调整数量</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

            <!-- ==================== Tab6: 调拨申请 ==================== -->
            <el-tab-pane label="调拨申请" name="transfer">
                <div class="toolbar">
                    <el-button type="success" :icon="Plus" @click="openCreateTransfer">发起调拨</el-button>
                    <el-button :icon="Refresh" @click="loadTransferOrders">刷新</el-button>
                </div>
                <el-table :data="transferOrders" stripe v-loading="loadingTransfer">
                    <el-table-column prop="orderNo" label="调拨单号" width="200" />
                    <el-table-column label="调出仓库" width="150">
                        <template #default="{ row }">{{ warehouseMap[row.srcWarehouseId] }}</template>
                    </el-table-column>
                    <el-table-column label="调入仓库" width="150">
                        <template #default="{ row }">{{ warehouseMap[row.dstWarehouseId] }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag size="small" :type="transferStatusTypeMap[row.status]">{{ transferStatusLabelMap[row.status] }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="createTime" label="创建时间" width="160" />
                    <el-table-column label="操作" width="180" align="center">
                        <template #default="{ row }">
                            <el-button size="small" @click="viewItems(row.id, 'transfer', row.orderNo)">明细</el-button>
                            <el-button v-if="row.status === 'APPROVED'" size="small" type="success" @click="handleCompleteTransfer(row.id)">执行调拨</el-button>
                            <el-button v-if="['PENDING','APPROVED'].includes(row.status)" size="small" type="danger" plain @click="handleCancelTransfer(row.id)">取消</el-button>
                        </template>
                    </el-table-column>
                </el-table>
            </el-tab-pane>

        </el-tabs>

        <!-- 库位编辑弹窗（字段与后端 warehouse_location 一致） -->
        <el-dialog v-model="locationDialogVisible" :title="locationForm.id ? '编辑库位' : '添加库位'" width="440px" align-center>
            <el-form :model="locationForm" label-width="90px">
                <el-form-item label="区域编码"><el-input v-model="locationForm.zoneCode" placeholder="如 A、B、冷藏区" /></el-form-item>
                <el-form-item label="排号"><el-input v-model="locationForm.rowNo" placeholder="如 01" /></el-form-item>
                <el-form-item label="架号"><el-input v-model="locationForm.shelfNo" placeholder="如 01" /></el-form-item>
                <el-form-item label="层号"><el-input v-model="locationForm.levelNo" placeholder="如 01" /></el-form-item>
                <el-form-item label="容量(件)"><el-input-number v-model="locationForm.capacity" :min="1" style="width:100%" /></el-form-item>
                <el-form-item label="库位编码"><el-input v-model="locationForm.locationCode" placeholder="如 A-01-02-03" /></el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="locationDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSaveLocation">保存</el-button>
            </template>
        </el-dialog>

        <!-- 完成入库：实际数量与上架库位 -->
        <el-dialog v-model="completeInboundVisible" title="完成入库" width="640px" align-center>
            <el-table :data="completeInboundRows" size="small" border>
                <el-table-column prop="productName" label="商品" min-width="120" />
                <el-table-column prop="expectedQty" label="预计" width="72" align="center" />
                <el-table-column label="实际" width="100" align="center">
                    <template #default="{ row }">
                        <el-input-number v-model="row.actualQty" :min="0" size="small" style="width:90px" />
                    </template>
                </el-table-column>
                <el-table-column label="上架库位" min-width="160">
                    <template #default="{ row }">
                        <el-select v-model="row.locationId" clearable filterable placeholder="可选" size="small" style="width:100%">
                            <el-option v-for="loc in completeInboundLocations" :key="loc.id"
                                :label="loc.locationCode + (loc.zoneCode ? ` (${loc.zoneCode}区)` : '')" :value="loc.id" />
                        </el-select>
                    </template>
                </el-table-column>
            </el-table>
            <template #footer>
                <el-button @click="completeInboundVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitCompleteInbound">确认入库</el-button>
            </template>
        </el-dialog>

        <!-- 新建入库单弹窗 -->
        <el-dialog v-model="createInboundVisible" title="新建入库单" width="620px" align-center>
            <el-form :model="inboundForm" label-width="100px">
                <el-form-item label="仓库" required>
                    <el-select v-model="inboundForm.warehouseId" style="width:100%" @change="loadInboundFormLocations">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="来源类型">
                    <el-select v-model="inboundForm.sourceType" style="width:100%">
                        <el-option label="采购入库" value="PURCHASE" />
                        <el-option label="退货入库" value="RETURN" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="inboundForm.remark" type="textarea" /></el-form-item>
                <el-divider>商品明细（必须至少添加一件；库位可选）</el-divider>
                <div v-for="(item, idx) in inboundForm.items" :key="idx" class="item-row item-row-wide">
                    <el-select v-model="item.productId" filterable placeholder="搜索并选择商品"
                        @change="(val: number) => onPickProduct(val, item)" style="flex:1;min-width:160px">
                        <el-option v-for="p in productList" :key="p.id"
                            :label="p.productName" :value="p.id">
                            <span>{{ p.productName }}</span>
                            <span style="color:#909399;font-size:12px;margin-left:8px">¥{{ p.price ?? '' }}</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.expectedQty" :min="1" placeholder="数量" style="width:100px" />
                    <el-select v-model="item.locationId" clearable filterable placeholder="上架库位"
                        style="width:160px" :disabled="!inboundForm.warehouseId">
                        <el-option v-for="loc in locationsForInboundForm" :key="loc.id"
                            :label="loc.locationCode" :value="loc.id" />
                    </el-select>
                    <el-button :icon="Delete" type="danger" plain circle size="small" @click="inboundForm.items.splice(idx,1)" />
                </div>
                <el-alert v-if="inboundForm.items.length === 0" type="warning" show-icon :closable="false"
                    title="请至少添加一个商品明细" style="margin-bottom:8px" />
                <el-button size="small" :icon="Plus" @click="inboundForm.items.push({productId:null,productName:'',expectedQty:1,locationId:null})">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createInboundVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateInbound">提交</el-button>
            </template>
        </el-dialog>

        <!-- 发起调拨弹窗 -->
        <el-dialog v-model="createTransferVisible" title="发起调拨" width="560px" align-center>
            <el-alert type="info" show-icon :closable="false" style="margin-bottom:12px">
                <template #title>商家发起的调拨将直接进入待执行状态，无需管理员审核</template>
            </el-alert>
            <el-form :model="transferForm" label-width="100px">
                <el-form-item label="调出仓库" required>
                    <el-select v-model="transferForm.srcWarehouseId" style="width:100%"
                        @change="onSrcWarehouseChange">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="调入仓库" required>
                    <el-select v-model="transferForm.dstWarehouseId" style="width:100%">
                        <!-- 排除已选调出仓库 -->
                        <el-option v-for="w in myWarehouses.filter(w => w.id !== transferForm.srcWarehouseId)"
                            :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="transferForm.remark" type="textarea" /></el-form-item>
                <el-divider>调拨商品（仅显示调出仓库有库存的商品）</el-divider>
                <div v-if="transferForm.srcWarehouseId && srcWarehouseStock.length === 0" style="color:#909399;font-size:13px;margin-bottom:8px">
                    调出仓库暂无库存商品
                </div>
                <div v-for="(item, idx) in transferForm.items" :key="idx" class="item-row">
                    <el-select v-model="item.productId" filterable placeholder="选择调出仓库的商品"
                        @change="(val: number) => onPickTransferProduct(val, item)" style="flex:1"
                        :disabled="!transferForm.srcWarehouseId">
                        <el-option v-for="s in srcWarehouseStock" :key="s.productId"
                            :label="s.productName" :value="s.productId">
                            <span>{{ s.productName }}</span>
                            <span style="color:#67c23a;font-size:12px;margin-left:8px">库存 {{ s.stock }} 件</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.quantity" :min="1"
                        :max="getStockMax(item.productId)" placeholder="数量" style="width:110px" />
                    <el-button :icon="Delete" type="danger" plain circle size="small" @click="transferForm.items.splice(idx,1)" />
                </div>
                <el-alert v-if="transferForm.items.length === 0" type="warning" show-icon :closable="false"
                    title="请至少添加一个调拨商品" style="margin-bottom:8px" />
                <el-button size="small" :icon="Plus"
                    :disabled="!transferForm.srcWarehouseId || srcWarehouseStock.length === 0"
                    @click="transferForm.items.push({productId:null,productName:'',quantity:1})">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createTransferVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateTransfer">提交并自动审批</el-button>
            </template>
        </el-dialog>

        <!-- 明细查看弹窗 -->
        <el-dialog v-model="itemsDialogVisible" :title="itemsDialogTitle" width="560px">
            <el-table :data="currentItems" stripe size="small">
                <el-table-column prop="productId" label="商品ID" width="80" />
                <el-table-column prop="productName" label="商品名称" min-width="140" />
                <el-table-column v-if="itemsType === 'inbound'" prop="expectedQty" label="预计" width="80" align="center" />
                <el-table-column v-if="itemsType === 'inbound'" prop="actualQty" label="实际" width="80" align="center" />
                <el-table-column v-if="itemsType !== 'inbound'" prop="quantity" label="数量" width="80" align="center" />
                <el-table-column v-if="itemsType === 'inbound' || itemsType === 'outbound'" prop="locationId" label="库位ID" width="88" align="center" />
                <el-table-column label="状态" width="80" align="center">
                    <template #default="{ row }">
                        <el-tag size="small" :type="row.status === 'DONE' ? 'success' : 'info'">{{ row.status }}</el-tag>
                    </template>
                </el-table-column>
            </el-table>
        </el-dialog>

        <!-- 新建出库单（商家） -->
        <el-dialog v-model="createOutboundVisible" title="新建出库单" width="620px" align-center>
            <el-form :model="outboundForm" label-width="100px">
                <el-form-item label="仓库" required>
                    <el-select v-model="outboundForm.warehouseId" style="width:100%" @change="loadOutboundFormLocations">
                        <el-option v-for="w in myWarehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
                    </el-select>
                </el-form-item>
                <el-form-item label="目的类型">
                    <el-select v-model="outboundForm.destType" style="width:100%">
                        <el-option label="配送出库" value="DELIVERY" />
                        <el-option label="退货出库" value="RETURN" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注"><el-input v-model="outboundForm.remark" type="textarea" /></el-form-item>
                <el-divider>商品明细（拣货库位可选）</el-divider>
                <div v-for="(item, idx) in outboundForm.items" :key="idx" class="item-row item-row-wide">
                    <el-select v-model="item.productId" filterable placeholder="选择商品"
                        @change="(val: number) => onPickProduct(val, item)" style="flex:1;min-width:160px">
                        <el-option v-for="p in productList" :key="p.id" :label="p.productName" :value="p.id">
                            <span>{{ p.productName }}</span>
                        </el-option>
                    </el-select>
                    <el-input-number v-model="item.quantity" :min="1" style="width:100px" />
                    <el-select v-model="item.locationId" clearable filterable placeholder="拣货库位"
                        style="width:160px" :disabled="!outboundForm.warehouseId">
                        <el-option v-for="loc in locationsForOutboundForm" :key="loc.id"
                            :label="loc.locationCode" :value="loc.id" />
                    </el-select>
                    <el-button :icon="Delete" type="danger" plain circle size="small" @click="outboundForm.items.splice(idx,1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="outboundForm.items.push({productId:null,productName:'',quantity:1,locationId:null})">添加商品</el-button>
            </el-form>
            <template #footer>
                <el-button @click="createOutboundVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleCreateOutbound">提交</el-button>
            </template>
        </el-dialog>

        <!-- 库存调整 -->
        <el-dialog v-model="stockEditVisible" title="调整库存数量" width="400px" align-center>
            <el-form v-if="stockEditRow" label-width="90px">
                <el-form-item label="商品"><span>{{ stockEditRow.productName }}</span></el-form-item>
                <el-form-item label="当前库存"><span>{{ stockEditRow.stock }}</span></el-form-item>
                <el-form-item label="新数量" required>
                    <el-input-number v-model="stockEditQty" :min="0" style="width:100%" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="stockEditVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="handleSaveStockAdjust">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="ShopWarehouse">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search, Delete } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/userStore';
import {
    getWarehousesByShop, getMyShop, getProducts,
    getLocationsByWarehouse, createLocation, updateLocation, deleteLocation,
    getStockDetailByWarehouse,
    getInboundOrders, getInboundOrderItems, createInboundOrder, startInbound, completeInbound, cancelInbound,
    getOutboundOrders, getOutboundOrderItems, createOutboundOrder, startOutbound, completeOutbound, cancelOutbound,
    getTransferOrders, getTransferOrderItems, createTransferOrder,
    approveTransfer, completeTransfer, cancelTransfer,
    updateStock
} from '@/api/shop';

const userStore = useUserStore();
const activeTab = ref('my-warehouses');
const saving = ref(false);

const myWarehouses = ref<any[]>([]);
const loadingWarehouses = ref(false);
const shopId = ref<number | null>(null);
const warehouseMap = computed(() => {
    const m: Record<number, string> = {};
    myWarehouses.value.forEach(w => { if (w.id) m[w.id] = w.warehouseName; });
    return m;
});

const statusTypeMap: Record<string, string> = { PENDING: 'info', PROCESSING: 'warning', DONE: 'success', CANCELLED: 'danger' };
const statusLabelMap: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', DONE: '已完成', CANCELLED: '已取消' };
const transferStatusTypeMap: Record<string, string> = { PENDING: 'warning', APPROVED: 'primary', IN_TRANSIT: 'warning', DONE: 'success', CANCELLED: 'danger' };
const transferStatusLabelMap: Record<string, string> = { PENDING: '待执行', APPROVED: '待执行', IN_TRANSIT: '在途', DONE: '已完成', CANCELLED: '已取消' };

// 商品列表（入库用，显示所有商品）
const productList = ref<any[]>([]);
const loadProducts = async () => {
    if (!shopId.value) return;
    try {
        const res = await getProducts({ shopId: shopId.value, size: 200, page: 1 });
        const raw = res.data;
        productList.value = raw?.records ?? raw ?? [];
    } catch { /* 静默失败 */ }
};
const onPickProduct = (productId: number, item: any) => {
    const p = productList.value.find((p: any) => p.id === productId);
    if (p) item.productName = p.productName;
};

// 调出仓库库存（调拨用，仅显示源仓库有库存的商品）
const srcWarehouseStock = ref<any[]>([]);
const onSrcWarehouseChange = async (warehouseId: number) => {
    transferForm.items = [];
    srcWarehouseStock.value = [];
    if (!warehouseId) return;
    try {
        const res = await getStockDetailByWarehouse(warehouseId, shopId.value ?? undefined);
        srcWarehouseStock.value = (res.data ?? []).filter((s: any) => s.stock > 0);
    } catch { /* 静默 */ }
};
const onPickTransferProduct = (productId: number, item: any) => {
    const s = srcWarehouseStock.value.find((s: any) => s.productId === productId);
    if (s) { item.productName = s.productName; item.quantity = Math.min(item.quantity || 1, s.stock); }
};
const getStockMax = (productId: number | null): number => {
    if (!productId) return 9999;
    const s = srcWarehouseStock.value.find((s: any) => s.productId === productId);
    return s?.stock ?? 9999;
};

const loadMyWarehouses = async () => {
    if (!shopId.value) return;
    loadingWarehouses.value = true;
    try {
        const res = await getWarehousesByShop(shopId.value);
        myWarehouses.value = res.data ?? [];
    } finally { loadingWarehouses.value = false; }
};

// ==================== 库位管理 ====================
const locWarehouseId = ref<number | null>(null);
const locations = ref<any[]>([]);
const loadingLocations = ref(false);
const locationDialogVisible = ref(false);
const locationForm = reactive<any>({
    id: undefined, warehouseId: null as number | null,
    zoneCode: '', rowNo: '', shelfNo: '', levelNo: '', capacity: 100, locationCode: '', status: 1
});

const goToLocations = (w: any) => {
    locWarehouseId.value = w.id;
    activeTab.value = 'locations';
    loadLocations();
};

const loadLocations = async () => {
    if (!locWarehouseId.value) return;
    loadingLocations.value = true;
    try {
        const res = await getLocationsByWarehouse(locWarehouseId.value);
        locations.value = res.data ?? [];
    } finally { loadingLocations.value = false; }
};

const openLocationDialog = (row?: any) => {
    if (row) { Object.assign(locationForm, row); }
    else {
        Object.assign(locationForm, {
            id: undefined,
            warehouseId: locWarehouseId.value,
            zoneCode: '', rowNo: '', shelfNo: '', levelNo: '', capacity: 100, locationCode: '', status: 1
        });
    }
    locationDialogVisible.value = true;
};

const handleSaveLocation = async () => {
    if (!locationForm.zoneCode?.trim() || !locationForm.locationCode?.trim()) {
        ElMessage.warning('请填写区域编码与库位编码');
        return;
    }
    saving.value = true;
    try {
        if (locationForm.id) { await updateLocation(locationForm.id, { ...locationForm }); ElMessage.success('库位已更新'); }
        else { await createLocation({ ...locationForm, warehouseId: locWarehouseId.value }); ElMessage.success('库位已创建'); }
        locationDialogVisible.value = false;
        loadLocations();
    } finally { saving.value = false; }
};

const handleDeleteLocation = async (id: number) => {
    await ElMessageBox.confirm('确认删除该库位？', '警告', { type: 'warning' });
    await deleteLocation(id); ElMessage.success('已删除'); loadLocations();
};

// ==================== 通用校验 ====================
function validateItems(items: any[], label: string): boolean {
    if (!items || items.length === 0) {
        ElMessage.warning(`请至少添加一个${label}明细`);
        return false;
    }
    if (items.some((i: any) => !i.productId)) {
        ElMessage.warning('有明细未选择商品，请补充后提交');
        return false;
    }
    return true;
}

function apiErrorMessage(e: any, fallback: string) {
    if (typeof e === 'string' && e.length) return e;
    return e?.response?.data?.message ?? e?.message ?? fallback;
}

// ==================== 入库 ====================
const inboundOrders = ref<any[]>([]);
const loadingInbound = ref(false);
const inboundFilter = reactive({ warehouseId: null as number | null });
const createInboundVisible = ref(false);
const inboundForm = reactive<any>({ warehouseId: null, sourceType: 'PURCHASE', remark: '', items: [] });

const loadInboundOrders = async () => {
    if (!shopId.value) return;
    loadingInbound.value = true;
    try {
        const res = await getInboundOrders({ shopId: shopId.value, warehouseId: inboundFilter.warehouseId || undefined });
        inboundOrders.value = res.data ?? [];
    } finally { loadingInbound.value = false; }
};
const openCreateInbound = () => {
    Object.assign(inboundForm, { warehouseId: null, sourceType: 'PURCHASE', remark: '', items: [] });
    locationsForInboundForm.value = [];
    createInboundVisible.value = true;
};
const handleCreateInbound = async () => {
    if (!inboundForm.warehouseId) { ElMessage.warning('请选择仓库'); return; }
    if (!validateItems(inboundForm.items, '商品')) return;
    saving.value = true;
    try {
        await createInboundOrder({ ...inboundForm, shopId: shopId.value });
        ElMessage.success('入库单已创建');
        createInboundVisible.value = false;
        loadInboundOrders();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '创建失败'));
    } finally { saving.value = false; }
};
const handleStartInbound = async (id: number) => {
    try {
        await startInbound(id);
        ElMessage.success('已开始入库');
        loadInboundOrders();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '操作失败'));
    }
};

const completeInboundVisible = ref(false);
const completeInboundOrderId = ref<number | null>(null);
const completeInboundRows = ref<any[]>([]);
const completeInboundLocations = ref<any[]>([]);

const openCompleteInbound = async (row: any) => {
    completeInboundOrderId.value = row.id;
    try {
        const res = await getInboundOrderItems(row.id);
        completeInboundRows.value = (res.data ?? []).map((i: any) => ({
            ...i,
            actualQty: i.expectedQty ?? i.actualQty ?? 0,
            locationId: i.locationId ?? null
        }));
        const locRes = await getLocationsByWarehouse(row.warehouseId);
        completeInboundLocations.value = locRes.data ?? [];
        completeInboundVisible.value = true;
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '加载明细失败'));
    }
};

const submitCompleteInbound = async () => {
    if (!completeInboundOrderId.value) return;
    const bad = completeInboundRows.value.some((r: any) => r.actualQty == null || r.actualQty < 0);
    if (bad) {
        ElMessage.warning('请填写每条明细的实际数量（≥0）');
        return;
    }
    saving.value = true;
    try {
        await completeInbound(completeInboundOrderId.value, completeInboundRows.value);
        ElMessage.success('入库完成，库存已更新');
        completeInboundVisible.value = false;
        loadInboundOrders();
        loadMyStock();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '完成入库失败'));
    } finally { saving.value = false; }
};
const handleCancelInbound = async (id: number) => {
    await ElMessageBox.confirm('确认取消？', '警告', { type: 'warning' });
    await cancelInbound(id); ElMessage.success('已取消'); loadInboundOrders();
};

// ==================== 出库 ====================
const outboundOrders = ref<any[]>([]);
const loadingOutbound = ref(false);
const outboundFilter = reactive({ warehouseId: null as number | null });
const createOutboundVisible = ref(false);
const outboundForm = reactive<any>({
    warehouseId: null as number | null,
    destType: 'DELIVERY',
    remark: '',
    items: [] as any[]
});

/** 入库/出库表单中选用的库位列表 */
const locationsForInboundForm = ref<any[]>([]);
const locationsForOutboundForm = ref<any[]>([]);

const loadInboundFormLocations = async () => {
    locationsForInboundForm.value = [];
    if (!inboundForm.warehouseId) return;
    try {
        const res = await getLocationsByWarehouse(inboundForm.warehouseId);
        locationsForInboundForm.value = res.data ?? [];
    } catch { /* 静默 */ }
};

const loadOutboundFormLocations = async () => {
    locationsForOutboundForm.value = [];
    if (!outboundForm.warehouseId) return;
    try {
        const res = await getLocationsByWarehouse(outboundForm.warehouseId);
        locationsForOutboundForm.value = res.data ?? [];
    } catch { /* 静默 */ }
};

watch(() => inboundForm.warehouseId, () => { loadInboundFormLocations(); });
watch(() => outboundForm.warehouseId, () => { loadOutboundFormLocations(); });

const loadOutboundOrders = async () => {
    if (!shopId.value) return;
    loadingOutbound.value = true;
    try {
        const res = await getOutboundOrders({ shopId: shopId.value, warehouseId: outboundFilter.warehouseId || undefined });
        outboundOrders.value = res.data ?? [];
    } finally { loadingOutbound.value = false; }
};

const openCreateOutbound = () => {
    Object.assign(outboundForm, { warehouseId: null, destType: 'DELIVERY', remark: '', items: [] });
    locationsForOutboundForm.value = [];
    createOutboundVisible.value = true;
};

const handleCreateOutbound = async () => {
    if (!outboundForm.warehouseId) { ElMessage.warning('请选择仓库'); return; }
    if (!validateItems(outboundForm.items as any[], '出库商品')) return;
    saving.value = true;
    try {
        await createOutboundOrder({
            ...outboundForm,
            shopId: shopId.value,
            items: outboundForm.items
        });
        ElMessage.success('出库单已创建');
        createOutboundVisible.value = false;
        loadOutboundOrders();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '创建出库单失败'));
    } finally { saving.value = false; }
};

const handleStartOutbound = async (id: number) => {
    try {
        await startOutbound(id);
        ElMessage.success('已开始拣货');
        loadOutboundOrders();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '操作失败'));
    }
};

const handleCompleteOutbound = async (id: number) => {
    try {
        await completeOutbound(id);
        ElMessage.success('出库完成');
        loadOutboundOrders();
        loadMyStock();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '完成出库失败'));
    }
};

const handleCancelOutbound = async (id: number) => {
    await ElMessageBox.confirm('确认取消出库单？', '警告', { type: 'warning' });
    try {
        await cancelOutbound(id);
        ElMessage.success('已取消');
        loadOutboundOrders();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '取消失败'));
    }
};

// ==================== 库存（本商家） ====================
const stockWarehouseId = ref<number | null>(null);
const myStockList = ref<any[]>([]);
const loadingStock = ref(false);
const stockEditVisible = ref(false);
const stockEditRow = ref<any>(null);
const stockEditQty = ref(0);

const loadMyStock = async () => {
    if (!shopId.value || !stockWarehouseId.value) {
        myStockList.value = [];
        return;
    }
    loadingStock.value = true;
    try {
        const res = await getStockDetailByWarehouse(stockWarehouseId.value, shopId.value);
        myStockList.value = res.data ?? [];
    } catch {
        myStockList.value = [];
    } finally { loadingStock.value = false; }
};

const openStockEdit = (row: any) => {
    stockEditRow.value = row;
    stockEditQty.value = row.stock ?? 0;
    stockEditVisible.value = true;
};

const handleSaveStockAdjust = async () => {
    if (!stockWarehouseId.value || !stockEditRow.value?.productId) return;
    saving.value = true;
    try {
        await updateStock({
            warehouseId: stockWarehouseId.value,
            productId: stockEditRow.value.productId,
            stock: stockEditQty.value
        });
        ElMessage.success('库存已更新');
        stockEditVisible.value = false;
        loadMyStock();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '更新库存失败'));
    } finally { saving.value = false; }
};

// ==================== 调拨 ====================
const transferOrders = ref<any[]>([]);
const loadingTransfer = ref(false);
const createTransferVisible = ref(false);
const transferForm = reactive<any>({ srcWarehouseId: null, dstWarehouseId: null, remark: '', items: [] });

const loadTransferOrders = async () => {
    if (!shopId.value) return;
    loadingTransfer.value = true;
    try {
        const res = await getTransferOrders({ shopId: shopId.value });
        transferOrders.value = res.data ?? [];
    } finally { loadingTransfer.value = false; }
};

const openCreateTransfer = () => {
    Object.assign(transferForm, { srcWarehouseId: null, dstWarehouseId: null, remark: '', items: [] });
    srcWarehouseStock.value = [];
    createTransferVisible.value = true;
};

const handleCreateTransfer = async () => {
    if (!transferForm.srcWarehouseId) { ElMessage.warning('请选择调出仓库'); return; }
    if (!transferForm.dstWarehouseId) { ElMessage.warning('请选择调入仓库'); return; }
    if (transferForm.srcWarehouseId === transferForm.dstWarehouseId) {
        ElMessage.warning('调出仓库和调入仓库不能相同'); return;
    }
    if (!validateItems(transferForm.items, '调拨商品')) return;
    // 校验数量不超过库存
    for (const item of transferForm.items) {
        const max = getStockMax(item.productId);
        if (item.quantity > max) {
            ElMessage.warning(`商品「${item.productName}」调拨数量 (${item.quantity}) 超过库存 (${max})`);
            return;
        }
    }
    saving.value = true;
    try {
        const res = await createTransferOrder({ ...transferForm, shopId: shopId.value });
        const orderId = res.data?.id;
        if (orderId) await approveTransfer(orderId);
        ElMessage.success('调拨单已提交，可直接点击「执行调拨」完成');
        createTransferVisible.value = false;
        loadTransferOrders();
    } finally { saving.value = false; }
};

const handleCompleteTransfer = async (id: number) => {
    await ElMessageBox.confirm('确认执行调拨？将立即转移库存并生成入库/出库记录。', '执行调拨',
        { type: 'warning', confirmButtonText: '确认执行' });
    try {
        await completeTransfer(id);
        ElMessage.success('调拨完成，库存已转移，入库/出库单已自动生成');
        loadTransferOrders(); loadInboundOrders(); loadOutboundOrders();
        loadMyStock();
    } catch (e: any) {
        ElMessage.error(apiErrorMessage(e, '调拨执行失败'));
    }
};

const handleCancelTransfer = async (id: number) => {
    await ElMessageBox.confirm('确认取消调拨？', '警告', { type: 'warning' });
    await cancelTransfer(id); ElMessage.success('已取消'); loadTransferOrders();
};

// ==================== 明细弹窗 ====================
const itemsDialogVisible = ref(false);
const itemsDialogTitle = ref('');
const itemsType = ref('');
const currentItems = ref<any[]>([]);
const viewItems = async (id: number, type: string, no: string) => {
    itemsType.value = type;
    itemsDialogTitle.value = `${type === 'inbound' ? '入库' : type === 'outbound' ? '出库' : '调拨'}单明细 - ${no}`;
    let res: any;
    if (type === 'inbound') res = await getInboundOrderItems(id);
    else if (type === 'outbound') res = await getOutboundOrderItems(id);
    else res = await getTransferOrderItems(id);
    currentItems.value = res.data ?? [];
    itemsDialogVisible.value = true;
};

onMounted(async () => {
    const userId = userStore.userInfo?.userId;
    if (userId) {
        const shopRes = await getMyShop(userId);
        shopId.value = shopRes.data?.id ?? null;
        await loadMyWarehouses();
        await loadProducts();
        loadInboundOrders();
        loadOutboundOrders();
        loadTransferOrders();
    }
});
</script>

<style scoped>
.shop-warehouse { display: flex; flex-direction: column; }
.main-tabs { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.item-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.item-row-wide { flex-wrap: wrap; }
</style>
