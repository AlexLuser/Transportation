import request from '@/utils/request';

/** 按订单 ID 查询物流路线详情（节点、计划路径等，与 logistics-service 一致） */
export const getRouteByOrderId = (orderId: number) => {
    return request({ url: `/logistics/routes/order/${orderId}`, method: 'GET' });
};

/**
 * 查询订单全程物流追踪（所有路线段按时间升序）
 * 返回 RouteDetailDTO[] — 从发货仓库到最终交付的完整物流链。
 */
export const getOrderJourney = (orderId: number) => {
    return request({ url: `/logistics/routes/order/${orderId}/journey`, method: 'GET' });
};

/** 按路线 ID 查询物流路线详情（干线任务 orderId=null 时使用） */
export const getRouteByRouteId = (routeId: number) => {
    return request({ url: `/logistics/routes/${routeId}`, method: 'GET' });
};

export interface CreateRouteRequest {
    orderId: number;
    warehouseId: number;
    startAddress: string;
    startLatitude?: number;
    startLongitude?: number;
    endAddress: string;
    endLatitude?: number;
    endLongitude?: number;
    receiverName?: string;
    receiverPhone?: string;
    /** 计划发货时间（ISO 8601，如 "2026-04-08T08:30:00"），不传则后端取当前时间 */
    plannedShipTime?: string;
}

/**
 * 创建物流路线（发货时调用，触发 LLM 路线规划策略）
 * data 为 { route, llmEnhanced, llmDecision }，LLM 元数据仅本次响应可见、不入库
 */
export const createRoute = (data: CreateRouteRequest) => {
    return request({ url: '/logistics/routes', method: 'POST', data });
};

export interface LocationUpdateRequest {
    routeId: number;
    latitude: number;
    longitude: number;
    altitude?: number;
    speed?: number;
    heading?: number;
    accuracy?: number;
    address?: string;
}

/** 运输员上报当前 GPS 位置（POST /logistics/track/location） */
export const uploadLocation = (data: LocationUpdateRequest) => {
    return request({ url: '/logistics/track/location', method: 'POST', data });
};

/** 获取路线最新轨迹点 */
export const getLatestTrack = (routeId: number) => {
    return request({ url: `/logistics/track/${routeId}/latest`, method: 'GET' });
};

// ── Hub-and-Spoke 相关接口 ────────────────────────────────────────

/** 获取所有中转站列表 */
export const getHubs = () => {
    return request({ url: '/logistics/hubs', method: 'GET' });
};

/** 查询最近中转站 */
export const getNearestHub = (lat: number, lng: number) => {
    return request({ url: '/logistics/hubs/nearest', method: 'GET', params: { lat, lng } });
};

/** 创建中转站（admin） */
export const createHub = (data: any) => {
    return request({ url: '/logistics/hubs', method: 'POST', data });
};

/** 更新中转站（admin） */
export const updateHub = (id: number, data: any) => {
    return request({ url: `/logistics/hubs/${id}`, method: 'PUT', data });
};

/** 删除中转站（admin） */
export const deleteHub = (id: number) => {
    return request({ url: `/logistics/hubs/${id}`, method: 'DELETE' });
};

export interface BatchOrderItem {
    orderId: number;
    endLat: number;
    endLng: number;
    endAddress: string;
    receiverName: string;
    receiverPhone: string;
}

export interface CreateBatchRequest {
    orderIds: number[];
    warehouseId: number;
    warehouseLat: number;
    warehouseLng: number;
    warehouseAddress: string;
    useHub: boolean;
    hubId?: number;
    strategy?: string;
    plannedShipTime?: string;
    orderItems: BatchOrderItem[];
}

/**
 * 创建配送批次（含VRP规划）
 * 返回 BatchDetailDTO：batch + hub + trunkRoute + lastMileRoutes + items + vrpResult
 */
export const createBatch = (data: CreateBatchRequest) => {
    return request({ url: '/logistics/batches', method: 'POST', data });
};

/** 获取批次详情 */
export const getBatchDetail = (batchId: number) => {
    return request({ url: `/logistics/batches/${batchId}`, method: 'GET' });
};

/** 获取批次下所有路线段（干线+末端） */
export const getBatchRoutes = (batchId: number) => {
    return request({ url: `/logistics/batches/${batchId}/routes`, method: 'GET' });
};

/** 干线司机确认到达中转站 */
export const arriveAtHub = (deliveryId: number) => {
    return request({ url: `/drivers/deliveries/${deliveryId}/arrive-hub`, method: 'PUT' });
};

// ── 智能调度相关接口 ─────────────────────────────────────────────

/** 查询调度池待调度订单列表；destHubId 为全国网收货 Hub（national_hub.id），与订单 dest_hub_id 一致 */
export const getDispatchPool = (destHubId?: number) => {
    return request({
        url: '/logistics/dispatch/pool',
        method: 'GET',
        params: destHubId != null ? { destHubId } : undefined,
    });
};

/**
 * 查询"揽收待处理"队列（配送中心作业看板专用）
 * 返回跨城订单中商家已发货、尚未进入干线的条目（is_cross_city=1, dispatch_origin_type=0）。
 * 与 getDispatchPool 互斥，覆盖订单生命周期的不同阶段。
 */
export const getCollectionQueue = (originHubId: number) => {
    return request({ url: '/logistics/dispatch/collection-queue', method: 'GET', params: { originHubId } });
};

/** 调度池订单紧急性检测：由 LLM 判断有备注的订单是否属于急送，返回 urgentOrderIds */
export const checkDispatchUrgency = (destHubId?: number) => {
    return request({
        url: '/logistics/dispatch/check-urgency',
        method: 'GET',
        params: destHubId != null ? { destHubId } : undefined,
    });
};

/** 调度预览：K-Means + LLM 建议；orderIds 为空则取全部待调度（受 destHubId 约束） */
export const previewDispatch = (
    k?: number,
    warehouseId?: number,
    orderIds?: number[],
    warehouseLat?: number,
    warehouseLng?: number,
    skipLlm?: boolean,
    destHubId?: number,
) => {
    return request({
        url: '/logistics/dispatch/preview',
        method: 'GET',
        params: {
            k,
            warehouseId,
            orderIds: orderIds?.join(',') || undefined,
            warehouseLat,
            warehouseLng,
            skipLlm: skipLlm || undefined,
            destHubId: destHubId != null ? destHubId : undefined,
        },
    });
};

export interface ExecuteBatchItem {
    orderIds: number[];
    warehouseId: number;
    warehouseLat: number;
    warehouseLng: number;
    warehouseAddress: string;
    useHub: boolean;
    hubId?: number;
    plannedShipTime?: string;
    orderItems?: BatchOrderItem[];
}

/** 执行调度：批量创建批次 */
export const executeDispatch = (batches: ExecuteBatchItem[]) => {
    return request({ url: '/logistics/dispatch/execute', method: 'POST', data: { batches } });
};

// ── 全国干线调度（MCMF）接口 ─────────────────────────────────────

/** 手动触发 MCMF 规划；llmReferenceDate 可选，作费用校准模型提示中的「今日」；天气/负载仍按后端真实取数 */
export const triggerNationalPlan = (planDate?: string, llmReferenceDate?: string) => {
    const params: Record<string, string> = {};
    if (planDate) params.planDate = planDate;
    if (llmReferenceDate) params.llmReferenceDate = llmReferenceDate;
    return request({
        url: '/logistics/dispatch/national/plan',
        method: 'POST',
        params,
    });
};

/** 查询当日干线批次列表 */
export const getNationalBatches = () => {
    return request({ url: '/logistics/dispatch/national/batches', method: 'GET' });
};

/** 标记发车 */
export const departBatch = (batchId: number) => {
    return request({ url: `/logistics/dispatch/national/${batchId}/depart`, method: 'POST' });
};

/** 标记到达 */
export const arriveBatch = (batchId: number) => {
    return request({ url: `/logistics/dispatch/national/${batchId}/arrive`, method: 'POST' });
};

/** 查询最新 MCMF 规划结果 */
export const getLatestFlowPlan = () => {
    return request({ url: '/logistics/dispatch/national/latest-plan', method: 'GET' });
};

/** 全国网 Hub 列表（MCMF 节点，与调度池 destHubId 同源） */
export const getNationalHubs = (level?: number) => {
    return request({
        url: '/logistics/national-network/hubs',
        method: 'GET',
        params: level != null ? { level } : undefined,
    });
};

/** 查询全国网络拓扑（节点+边） */
export const getNationalTopology = () => {
    return request({ url: '/logistics/national-network/topology', method: 'GET' });
};

// ── 配送中心作业记录（Hub Operations）───────────────────────────────

/** 查询 Hub 入库记录；status: PENDING | DONE */
export const getHubInboundRecords = (hubId?: number, status?: string) => {
    return request({ url: '/logistics/hub-operations/inbound', method: 'GET', params: { hubId, status } });
};

/** 创建 Hub 入库记录（揽收到仓 / 干线到达） */
export const createHubInboundRecord = (data: { hubId: number; orderId?: number; batchId?: number; remark?: string }) => {
    return request({ url: '/logistics/hub-operations/inbound', method: 'POST', data });
};

/** 确认 Hub 入库 */
export const confirmHubInbound = (id: number) => {
    return request({ url: `/logistics/hub-operations/inbound/${id}/confirm`, method: 'PUT' });
};

/** 查询 Hub 分拣记录；sortResult: PENDING | ASSIGNED */
export const getHubSortingRecords = (hubId?: number, sortResult?: string) => {
    return request({ url: '/logistics/hub-operations/sorting', method: 'GET', params: { hubId, sortResult } });
};

/** 创建 Hub 分拣记录 */
export const createHubSortingRecord = (data: { hubId: number; orderId?: number; destHubId?: number; remark?: string }) => {
    return request({ url: '/logistics/hub-operations/sorting', method: 'POST', data });
};

/** 完成分拣 — 分配到批次 */
export const assignHubSorting = (id: number, batchId: number) => {
    return request({ url: `/logistics/hub-operations/sorting/${id}/assign`, method: 'PUT', params: { batchId } });
};

/** 查询 Hub 出库记录；status: PENDING | DONE */
export const getHubOutboundRecords = (hubId?: number, status?: string) => {
    return request({ url: '/logistics/hub-operations/outbound', method: 'GET', params: { hubId, status } });
};
