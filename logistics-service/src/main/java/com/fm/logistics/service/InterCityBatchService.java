package com.fm.logistics.service;

import com.fm.logistics.dto.InterCityBatchDTO;

import java.util.List;

public interface InterCityBatchService {

    List<InterCityBatchDTO> listActiveBatches();

    /** 标记发车：status → DEPARTED，为批次内订单创建 segmentType=3 虚拟路线 */
    void onDeparture(Long batchId);

    /** 标记到达：status → ARRIVED，将订单写入目标城市 dispatch_pool */
    void onArrival(Long batchId);
}
