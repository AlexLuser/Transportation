<template>
  <div class="hub-ops-page">
    <!-- 顶部：Hub 选择 + 流程标题 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">中转站作业台</h2>
      </div>
      <div class="header-right">
        <span class="label">选择中转站：</span>
        <el-select v-model="selectedHubId" placeholder="请选择中转站" style="width:240px" @change="loadAll" clearable>
          <el-option v-for="h in hubList" :key="h.id" :label="h.name" :value="h.id">
            <span>{{ h.name }}</span>
            <span style="color:#909399;margin-left:8px;font-size:12px">{{ h.cityName }}</span>
          </el-option>
        </el-select>
        <el-button :icon="Refresh" circle @click="loadHubsWithOrders" :loading="loading" style="margin-left:8px" title="刷新并重新过滤配送中心列表" />
      </div>
    </div>

    <!-- 流程指示条 -->
    <div class="flow-bar">
      <div v-for="(stage, idx) in stages" :key="idx" class="flow-step" :class="{ active: activeStage === idx }" @click="activeStage = idx">
        <div class="step-icon" :style="{ background: stage.color }">
          <el-icon size="18"><component :is="stage.icon" /></el-icon>
        </div>
        <div class="step-info">
          <div class="step-name">{{ stage.name }}</div>
          <div class="step-count">{{ stage.count }} 件</div>
        </div>
        <div v-if="idx < stages.length - 1" class="step-arrow">→</div>
      </div>
    </div>

    <!-- 四阶段内容区 -->
    <el-row :gutter="16" class="pipeline-row">
      <!-- Stage 0：待揽收 -->
      <el-col :span="4">
        <div class="stage-card" :class="{ 'stage-active': activeStage === 0 }">
          <div class="stage-header" style="border-left:4px solid #e6a23c">
            <el-icon color="#e6a23c"><Van /></el-icon>
            <span class="stage-title">待揽收</span>
            <el-badge :value="poolItems.length" class="badge" />
          </div>
          <div class="stage-desc">商家已发货，等待运输员上门取件</div>
          <div class="stage-body">
            <div v-if="!selectedHubId" class="empty-hint">请先选择中转站</div>
            <div v-else-if="poolItems.length === 0" class="empty-hint">暂无待揽收订单</div>
            <div v-for="item in poolItems" :key="item.orderId" class="flow-card orange">
              <div class="card-top">
                <span class="order-no">订单 #{{ item.orderId }}</span>
                <el-tag size="small" type="warning">待揽收</el-tag>
              </div>
              <div class="card-row"><el-icon><Location /></el-icon> {{ item.endAddress || '目的地未知' }}</div>
              <div class="card-row"><el-icon><Tickets /></el-icon> 跨城：{{ item.isCrossCity ? '是' : '否' }}</div>
              <el-button size="small" type="warning" plain @click="doCreateInbound(item)" style="margin-top:6px;width:100%">
                确认已到仓 → 创建入库
              </el-button>
            </div>
          </div>
          <!-- 批量操作 -->
          <div class="stage-footer">
            <el-button
              size="small" type="warning" :icon="Check"
              :disabled="!selectedHubId || poolItems.length === 0"
              :loading="batchLoading.pool"
              @click="batchCreateInbound"
              style="width:100%"
            >一键全部到仓（{{ poolItems.length }} 笔）</el-button>
          </div>
        </div>
      </el-col>

      <!-- Stage 1：待入库 -->
      <el-col :span="4">
        <div class="stage-card" :class="{ 'stage-active': activeStage === 1 }">
          <div class="stage-header" style="border-left:4px solid #409eff">
            <el-icon color="#409eff"><Download /></el-icon>
            <span class="stage-title">待入库</span>
            <el-badge :value="inboundPending.length" class="badge" />
          </div>
          <div class="stage-desc">货物已到达中转站，待扫码入库</div>
          <div class="stage-body">
            <div v-if="!selectedHubId" class="empty-hint">请先选择中转站</div>
            <div v-else-if="inboundPending.length === 0" class="empty-hint">暂无待入库记录</div>
            <div v-for="rec in inboundPending" :key="rec.id" class="flow-card blue">
              <div class="card-top">
                <span class="order-no">入库单 #{{ rec.id }}</span>
                <el-tag size="small" type="primary">待确认</el-tag>
              </div>
              <div class="card-row" v-if="rec.orderId"><el-icon><Document /></el-icon> 关联订单 #{{ rec.orderId }}</div>
              <div class="card-row"><el-icon><Clock /></el-icon> {{ formatTime(rec.arriveTime) }} 到仓</div>
              <div v-if="rec.remark" class="card-row"><el-icon><ChatLineSquare /></el-icon> {{ rec.remark }}</div>
              <el-button size="small" type="primary" plain @click="doConfirmInbound(rec)" style="margin-top:6px;width:100%">
                确认入库 → 进入分拣
              </el-button>
            </div>
          </div>
          <!-- 手动创建入库 + 批量 -->
          <div class="stage-footer stage-footer-row">
            <el-button size="small" :icon="Plus" @click="showInboundDialog = true" :disabled="!selectedHubId">
              手动登记
            </el-button>
            <el-button
              size="small" type="primary" :icon="Check"
              :disabled="!selectedHubId || inboundPending.length === 0"
              :loading="batchLoading.inbound"
              @click="batchConfirmInbound"
            >全部入库（{{ inboundPending.length }}）</el-button>
          </div>
        </div>
      </el-col>

      <!-- Stage 2：待分拣 -->
      <el-col :span="4">
        <div class="stage-card" :class="{ 'stage-active': activeStage === 2 }">
          <div class="stage-header" style="border-left:4px solid #67c23a">
            <el-icon color="#67c23a"><Sort /></el-icon>
            <span class="stage-title">待分拣</span>
            <el-badge :value="sortingPending.length" class="badge" />
          </div>
          <div class="stage-desc">已完成入库，正在按目的地分拣</div>
          <div class="stage-body">
            <div v-if="!selectedHubId" class="empty-hint">请先选择中转站</div>
            <div v-else-if="sortingPending.length === 0" class="empty-hint">暂无待分拣记录</div>
            <div v-for="rec in sortingPending" :key="rec.id" class="flow-card green">
              <div class="card-top">
                <span class="order-no">分拣单 #{{ rec.id }}</span>
                <el-tag size="small" type="success">待分拣</el-tag>
              </div>
              <div class="card-row" v-if="rec.orderId"><el-icon><Document /></el-icon> 关联订单 #{{ rec.orderId }}</div>
              <div class="card-row" v-if="rec.destHubId"><el-icon><Aim /></el-icon> 目标中转站 #{{ rec.destHubId }}</div>
              <div class="card-row"><el-icon><Clock /></el-icon> {{ formatTime(rec.createTime) }} 登记</div>
              <div style="margin-top:6px;font-size:11px;color:#909399;text-align:center;padding:4px 0;background:#f5f5f5;border-radius:4px">
                <el-icon size="11"><InfoFilled /></el-icon> 前往「全国干线」立即规划，自动分配批次
              </div>
            </div>
          </div>
          <!-- 手动创建分拣 -->
          <div class="stage-footer">
            <el-button size="small" :icon="Plus" @click="showSortingDialog = true" :disabled="!selectedHubId">
              手动登记分拣
            </el-button>
          </div>
        </div>
      </el-col>

      <!-- Stage 3：待出库 -->
      <el-col :span="4">
        <div class="stage-card" :class="{ 'stage-active': activeStage === 3 }">
          <div class="stage-header" style="border-left:4px solid #9b59b6">
            <el-icon color="#9b59b6"><Upload /></el-icon>
            <span class="stage-title">待出库</span>
            <el-badge :value="outboundPending.length" class="badge" />
          </div>
          <div class="stage-desc">分拣完成，待出库装车发运</div>
          <div class="stage-body">
            <div v-if="!selectedHubId" class="empty-hint">请先选择中转站</div>
            <div v-else-if="outboundPending.length === 0" class="empty-hint">暂无待出库记录</div>
            <div v-for="rec in outboundPending" :key="rec.id" class="flow-card purple">
              <div class="card-top">
                <span class="order-no">出库单 #{{ rec.id }}</span>
                <el-tag size="small" type="warning">待出库</el-tag>
              </div>
              <div class="card-row" v-if="rec.orderId"><el-icon><Document /></el-icon> 关联订单 #{{ rec.orderId }}</div>
              <div class="card-row"><el-icon><Clock /></el-icon> {{ formatTime(rec.createTime) }} 登记</div>
              <div v-if="rec.remark" class="card-row"><el-icon><ChatLineSquare /></el-icon> {{ rec.remark }}</div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- Stage 4：干线运输 -->
      <el-col :span="4">
        <div class="stage-card" :class="{ 'stage-active': activeStage === 4 }">
          <div class="stage-header" style="border-left:4px solid #909399">
            <el-icon color="#909399"><Promotion /></el-icon>
            <span class="stage-title">干线运输</span>
            <el-badge :value="nationalBatches.length" class="badge" />
          </div>
          <div class="stage-desc">已分配至跨城批次，等待/正在长途运输</div>
          <div class="stage-body">
            <div v-if="!selectedHubId" class="empty-hint">请先选择中转站</div>
            <div v-else-if="nationalBatches.length === 0" class="empty-hint">暂无干线批次</div>
            <div v-for="batch in nationalBatches" :key="batch.id" class="flow-card gray">
              <div class="card-top">
                <span class="order-no">批次 #{{ batch.id }}</span>
                <el-tag size="small" :type="batchTagType(batch.status)">{{ batchStatusLabel(batch.status) }}</el-tag>
              </div>
              <div class="card-row"><el-icon><Location /></el-icon> {{ batch.fromHubName || '起点站' }} → {{ batch.toHubName || '目标站' }}</div>
              <div class="card-row"><el-icon><Tickets /></el-icon> 含 {{ batch.orderCount || 0 }} 票货</div>
              <div class="card-row"><el-icon><Clock /></el-icon> {{ formatTime(batch.plannedDepartTime) }} 计划发车</div>
              <div class="batch-actions">
                <el-button v-if="batch.status === 'CREATED'" size="small" type="primary" plain @click="doDepartBatch(batch.id)">标记发车</el-button>
                <el-button v-if="batch.status === 'DEPARTED'" size="small" type="success" plain @click="doArriveBatch(batch.id)">标记到达</el-button>
                <el-tag v-if="batch.status === 'ARRIVED'" type="success" size="small">已到达</el-tag>
              </div>
            </div>
          </div>
          <!-- 批量发车/到达 -->
          <div class="stage-footer stage-footer-row">
            <el-button
              size="small" type="primary" :icon="Promotion"
              :disabled="!selectedHubId || !nationalBatches.some(b => b.status === 'CREATED')"
              :loading="batchLoading.trunk"
              @click="batchTrunkAction('depart')"
            >批量发车（{{ nationalBatches.filter(b => b.status === 'CREATED').length }}）</el-button>
            <el-button
              size="small" type="success" :icon="Check"
              :disabled="!selectedHubId || !nationalBatches.some(b => b.status === 'DEPARTED')"
              :loading="batchLoading.trunk"
              @click="batchTrunkAction('arrive')"
            >批量到达（{{ nationalBatches.filter(b => b.status === 'DEPARTED').length }}）</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 手动创建入库记录弹窗 -->
    <el-dialog v-model="showInboundDialog" title="手动登记到货" width="420px">
      <el-form :model="inboundForm" label-width="90px">
        <el-form-item label="关联订单号">
          <el-input v-model.number="inboundForm.orderId" placeholder="选填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="inboundForm.remark" type="textarea" rows="2" placeholder="到货备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInboundDialog = false">取消</el-button>
        <el-button type="primary" @click="submitInbound">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 手动创建分拣记录弹窗 -->
    <el-dialog v-model="showSortingDialog" title="登记分拣作业" width="420px">
      <el-form :model="sortingForm" label-width="90px">
        <el-form-item label="关联订单号">
          <el-input v-model.number="sortingForm.orderId" placeholder="可选" />
        </el-form-item>
        <el-form-item label="目标中转站">
          <el-select v-model="sortingForm.destHubId" placeholder="请选择目标中转站" style="width:100%">
            <el-option v-for="h in hubList.filter(h => h.id !== selectedHubId)" :key="h.id" :label="h.name" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sortingForm.remark" type="textarea" rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSortingDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSorting">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 分配批次弹窗已废弃：MCMF 全国干线规划时自动完成分拣+批次绑定 -->
  </div>
</template>

<script setup lang="ts" name="HubOperations">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Refresh, Plus, Check, Van, Download, Upload, Sort, Promotion, Location, Tickets, Document, Clock,
  Aim, InfoFilled, ChatLineSquare
} from '@element-plus/icons-vue';
import {
  getNationalHubs, getCollectionQueue,
  getHubInboundRecords, createHubInboundRecord, confirmHubInbound,
  getHubSortingRecords, createHubSortingRecord,
  getHubOutboundRecords,
  getNationalBatches, departBatch, arriveBatch,
} from '@/api/logistics';

const loading = ref(false);
const batchLoading = ref({ pool: false, inbound: false, sorting: false, outbound: false, trunk: false });
const selectedHubId = ref<number | null>(null);
const hubList = ref<any[]>([]);

// 各阶段数据
const poolItems = ref<any[]>([]);
const inboundPending = ref<any[]>([]);
const sortingPending = ref<any[]>([]);
const outboundPending = ref<any[]>([]);
const nationalBatches = ref<any[]>([]);

// 弹窗控制
const showInboundDialog = ref(false);
const showSortingDialog = ref(false);

const inboundForm = ref({ orderId: null as number | null, remark: '' });
const sortingForm = ref({ orderId: null as number | null, destHubId: null as number | null, remark: '' });

const activeStage = ref(0);

const stages = computed(() => [
  { name: '待揽收', count: poolItems.value.length, color: '#e6a23c', icon: Van },
  { name: '待入库',   count: inboundPending.value.length, color: '#409eff', icon: Download },
  { name: '待分拣',   count: sortingPending.value.length, color: '#67c23a', icon: Sort },
  { name: '待出库',   count: outboundPending.value.length, color: '#9b59b6', icon: Upload },
  { name: '干线运输', count: nationalBatches.value.length, color: '#909399', icon: Promotion },
]);

onMounted(async () => {
  await loadHubsWithOrders();
});

/** 加载城市配送中心列表，并自动过滤：只显示存在待揽收订单的 Hub */
async function loadHubsWithOrders() {
  loading.value = true;
  try {
    const res = await getNationalHubs(2);
    const allHubs: any[] = res.data || [];
    if (allHubs.length === 0) return;

    // 并行查询每个 Hub 的待揽收数量
    const checks = await Promise.all(
      allHubs.map(h =>
        getCollectionQueue(h.id)
          .then(r => ({ hub: h, count: (r.data || []).length }))
          .catch(() => ({ hub: h, count: 0 }))
      )
    );

    // 只保留有待揽收订单的 Hub
    const filtered = checks.filter(c => c.count > 0).map(c => c.hub);
    hubList.value = filtered.length > 0 ? filtered : allHubs; // 若全部为空则显示全部，避免空选

    // 默认选第一个（有订单的优先）
    if (hubList.value.length > 0) {
      selectedHubId.value = hubList.value[0].id;
      await loadAll();
    }
  } finally {
    loading.value = false;
  }
}

async function loadAll() {
  if (!selectedHubId.value) return;
  loading.value = true;
  try {
    await Promise.all([loadPool(), loadInbound(), loadSorting(), loadOutbound(), loadBatches()]);
  } finally {
    loading.value = false;
  }
}

async function loadPool() {
  // 使用专用"揽收待处理"端点：跨城、商家已发货、尚未进入干线（dispatch_origin_type=0）
  const [poolRes, inboundRes] = await Promise.all([
    getCollectionQueue(selectedHubId.value!),
    getHubInboundRecords(selectedHubId.value!),  // 不限状态，获取所有已建入库记录
  ]);
  // 过滤：已有入库记录的订单从待揽收列表移除，避免重复操作
  const inboundedOrderIds = new Set(
    (inboundRes.data || []).map((r: any) => r.orderId).filter(Boolean)
  );
  poolItems.value = (poolRes.data || []).filter(
    (it: any) => !inboundedOrderIds.has(it.orderId)
  );
}

async function loadInbound() {
  const res = await getHubInboundRecords(selectedHubId.value!, 'PENDING');
  inboundPending.value = res.data || [];
}

async function loadSorting() {
  const res = await getHubSortingRecords(selectedHubId.value!, 'PENDING');
  sortingPending.value = res.data || [];
}

async function loadOutbound() {
  const res = await getHubOutboundRecords(selectedHubId.value!, 'PENDING');
  outboundPending.value = res.data || [];
}

async function loadBatches() {
  const res = await getNationalBatches();
  const all: any[] = res.data || [];
  nationalBatches.value = all.filter((b: any) =>
    b.fromHubId == selectedHubId.value && (b.status === 'CREATED' || b.status === 'DEPARTED')
  );
}

// 从揽收快速创建入库记录
async function doCreateInbound(item: any) {
  if (!selectedHubId.value) return;
  await createHubInboundRecord({ hubId: selectedHubId.value, orderId: item.orderId, remark: '由待揽收登记' });
  ElMessage.success(`订单 #${item.orderId} 已登记入库，等待确认`);
  await loadAll();
}

// 确认入库 + 自动创建分拣记录
async function doConfirmInbound(rec: any) {
  await confirmHubInbound(rec.id);
  // 同步创建分拣记录
  await createHubSortingRecord({ hubId: selectedHubId.value!, orderId: rec.orderId, remark: '入库后自动生成' });
  ElMessage.success('已确认入库，分拣记录已生成');
  await loadAll();
}

// 手动提交入库
async function submitInbound() {
  if (!selectedHubId.value) return;
  await createHubInboundRecord({ hubId: selectedHubId.value, ...inboundForm.value });
  ElMessage.success('到货记录已创建');
  showInboundDialog.value = false;
  inboundForm.value = { orderId: null, remark: '' };
  await loadInbound();
}

// 手动提交分拣
async function submitSorting() {
  if (!selectedHubId.value) return;
  await createHubSortingRecord({ hubId: selectedHubId.value, ...sortingForm.value });
  ElMessage.success('分拣记录已创建');
  showSortingDialog.value = false;
  sortingForm.value = { orderId: null, destHubId: null, remark: '' };
  await loadSorting();
}


// 干线批次操作
async function doDepartBatch(id: number) {
  await departBatch(id);
  ElMessage.success('已标记发车，干线运输中');
  await loadBatches();
}

async function doArriveBatch(id: number) {
  await arriveBatch(id);
  ElMessage.success('已标记到达目标配送中心');
  await loadBatches();
}

// 批量：待揽收 → 全部到仓
async function batchCreateInbound() {
  if (!poolItems.value.length) return;
  await ElMessageBox.confirm(
    `确认将待揽收列表中全部 ${poolItems.value.length} 笔订单批量登记入库？`,
    '批量确认到仓',
    { confirmButtonText: '全部入库', cancelButtonText: '取消', type: 'warning' }
  );
  batchLoading.value.pool = true;
  let ok = 0, fail = 0;
  try {
    await Promise.all(
      poolItems.value.map(item =>
        createHubInboundRecord({ hubId: selectedHubId.value!, orderId: item.orderId, remark: '批量待揽收入库' })
          .then(() => ok++)
          .catch(() => fail++)
      )
    );
    ElMessage.success(`批量完成：成功 ${ok} 笔${fail ? '，失败 ' + fail + ' 笔' : ''}`);
    await loadAll();
  } finally {
    batchLoading.value.pool = false;
  }
}

// 批量：待入库 → 全部确认入库
async function batchConfirmInbound() {
  if (!inboundPending.value.length) return;
  await ElMessageBox.confirm(
    `确认将待入库列表中全部 ${inboundPending.value.length} 条记录批量确认入库并生成分拣单？`,
    '批量确认入库',
    { confirmButtonText: '全部确认', cancelButtonText: '取消', type: 'warning' }
  );
  batchLoading.value.inbound = true;
  let ok = 0, fail = 0;
  try {
    await Promise.all(
      inboundPending.value.map(rec =>
        confirmHubInbound(rec.id)
          .then(() => createHubSortingRecord({ hubId: selectedHubId.value!, orderId: rec.orderId, remark: '批量入库后自动生成' }))
          .then(() => ok++)
          .catch(() => fail++)
      )
    );
    ElMessage.success(`批量完成：成功 ${ok} 笔${fail ? '，失败 ' + fail + ' 笔' : ''}`);
    await loadAll();
  } finally {
    batchLoading.value.inbound = false;
  }
}

// 批量：干线运输 → 全部发车 / 全部到达
async function batchTrunkAction(action: 'depart' | 'arrive') {
  const targets = nationalBatches.value.filter(b =>
    action === 'depart' ? b.status === 'CREATED' : b.status === 'DEPARTED'
  );
  if (!targets.length) return;
  const label = action === 'depart' ? '发车' : '到达';
  await ElMessageBox.confirm(
    `确认将 ${targets.length} 个批次批量标记「${label}」？`,
    `批量标记${label}`,
    { confirmButtonText: `全部${label}`, cancelButtonText: '取消', type: 'warning' }
  );
  batchLoading.value.trunk = true;
  let ok = 0, fail = 0;
  try {
    await Promise.all(
      targets.map(b =>
        (action === 'depart' ? departBatch(b.id) : arriveBatch(b.id))
          .then(() => ok++)
          .catch(() => fail++)
      )
    );
    ElMessage.success(`批量完成：成功 ${ok} 个${fail ? '，失败 ' + fail + ' 个' : ''}`);
    await loadBatches();
  } finally {
    batchLoading.value.trunk = false;
  }
}

function formatTime(t: any) {
  if (!t) return '—';
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function batchStatusLabel(s: string) {
  return { CREATED: '待发车', DEPARTED: '运输中', ARRIVED: '已到达' }[s] || s;
}

function batchTagType(s: string): any {
  return { CREATED: 'warning', DEPARTED: 'primary', ARRIVED: 'success' }[s] || 'info';
}
</script>

<style scoped>
.hub-ops-page {
  min-height: 100%;
  padding: 4px 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 4px;
  color: #303133;
}

.label {
  font-size: 13px;
  color: #606266;
  margin-right: 4px;
}

/* 流程指示条 */
.flow-bar {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 10px;
  padding: 14px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  gap: 0;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 8px;
  transition: background .15s;
  flex: 1;
}

.flow-step:hover,
.flow-step.active {
  background: #f0f2f5;
}

.step-icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.step-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.step-count {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.step-arrow {
  font-size: 20px;
  color: #c0c4cc;
  margin: 0 4px;
}

/* 看板列 */
.pipeline-row { margin-top: 0; }

.stage-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  transition: box-shadow .15s;
  height: calc(100vh - 300px);
  min-height: 420px;
  display: flex;
  flex-direction: column;
}

.stage-card.stage-active {
  box-shadow: 0 4px 16px rgba(0,0,0,.12);
}

.stage-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.stage-title {
  font-size: 14px;
  font-weight: 700;
  color: #303133;
  flex: 1;
}

.stage-desc {
  font-size: 12px;
  color: #909399;
  padding: 6px 14px;
  border-bottom: 1px solid #f5f5f5;
  background: #fafafa;
}

.stage-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stage-footer {
  padding: 8px 10px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.stage-footer .el-button {
  width: 100%;
}

.stage-footer-row {
  display: flex;
  gap: 6px;
}

.stage-footer-row .el-button {
  flex: 1;
  width: auto;
}

.empty-hint {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 30px 0;
}

/* 流程卡片 */
.flow-card {
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #eee;
  font-size: 12px;
}

.flow-card.orange { border-left: 3px solid #e6a23c; background: #fffbf2; }
.flow-card.blue   { border-left: 3px solid #409eff; background: #f0f7ff; }
.flow-card.green  { border-left: 3px solid #67c23a; background: #f0f9eb; }
.flow-card.purple { border-left: 3px solid #9b59b6; background: #f8f0ff; }
.flow-card.gray   { border-left: 3px solid #909399; background: #f8f8f8; }

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.order-no {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

.card-row {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.batch-actions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
}

/* 流程说明 */
</style>
