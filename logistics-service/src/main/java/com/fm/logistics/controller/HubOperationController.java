package com.fm.logistics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.common.result.Result;
import com.fm.logistics.entity.HubInboundRecord;
import com.fm.logistics.entity.HubOutboundRecord;
import com.fm.logistics.entity.HubSortingRecord;
import com.fm.logistics.mapper.HubInboundRecordMapper;
import com.fm.logistics.mapper.HubOutboundRecordMapper;
import com.fm.logistics.mapper.HubSortingRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Tag(name = "配送中心作业", description = "national_hub 入库/分拣/出库作业记录接口")
@RestController
@RequestMapping("/api/logistics/hub-operations")
public class HubOperationController {

    @Autowired
    private HubInboundRecordMapper inboundMapper;
    @Autowired
    private HubOutboundRecordMapper outboundMapper;
    @Autowired
    private HubSortingRecordMapper sortingMapper;

    // ==================== 入库 ====================

    @Operation(summary = "查询Hub入库记录")
    @GetMapping("/inbound")
    public Result<List<HubInboundRecord>> listInbound(
            @RequestParam(required = false) Long hubId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<HubInboundRecord> wrapper = new LambdaQueryWrapper<>();
        if (hubId != null) wrapper.eq(HubInboundRecord::getHubId, hubId);
        if (status != null) wrapper.eq(HubInboundRecord::getStatus, status);
        wrapper.orderByDesc(HubInboundRecord::getCreateTime);
        return Result.success(inboundMapper.selectList(wrapper));
    }

    @Operation(summary = "创建Hub入库记录（揽收到仓/干线到达）")
    @PostMapping("/inbound")
    public Result<HubInboundRecord> createInbound(@RequestBody HubInboundRecord record) {
        record.setStatus("PENDING");
        record.setArriveTime(new Date());
        inboundMapper.insert(record);
        return Result.success(record);
    }

    @Operation(summary = "确认Hub入库")
    @PutMapping("/inbound/{id}/confirm")
    public Result<Boolean> confirmInbound(@PathVariable Long id) {
        LambdaUpdateWrapper<HubInboundRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HubInboundRecord::getId, id)
                .set(HubInboundRecord::getStatus, "DONE")
                .set(HubInboundRecord::getInboundTime, new Date());
        return Result.success(inboundMapper.update(null, wrapper) > 0);
    }

    // ==================== 分拣 ====================

    @Operation(summary = "查询Hub分拣记录")
    @GetMapping("/sorting")
    public Result<List<HubSortingRecord>> listSorting(
            @RequestParam(required = false) Long hubId,
            @RequestParam(required = false) String sortResult) {
        LambdaQueryWrapper<HubSortingRecord> wrapper = new LambdaQueryWrapper<>();
        if (hubId != null) wrapper.eq(HubSortingRecord::getHubId, hubId);
        if (sortResult != null) wrapper.eq(HubSortingRecord::getSortResult, sortResult);
        wrapper.orderByDesc(HubSortingRecord::getCreateTime);
        return Result.success(sortingMapper.selectList(wrapper));
    }

    @Operation(summary = "创建分拣记录")
    @PostMapping("/sorting")
    public Result<HubSortingRecord> createSorting(@RequestBody HubSortingRecord record) {
        record.setSortResult("PENDING");
        sortingMapper.insert(record);
        return Result.success(record);
    }

    @Operation(summary = "完成分拣（分配到批次）")
    @PutMapping("/sorting/{id}/assign")
    public Result<Boolean> assignSorting(@PathVariable Long id,
                                          @RequestParam Long batchId,
                                          @RequestParam(defaultValue = "ASSIGNED") String result) {
        LambdaUpdateWrapper<HubSortingRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HubSortingRecord::getId, id)
                .set(HubSortingRecord::getAssignedBatchId, batchId)
                .set(HubSortingRecord::getSortResult, result)
                .set(HubSortingRecord::getSortTime, new Date());
        return Result.success(sortingMapper.update(null, wrapper) > 0);
    }

    // ==================== 出库 ====================

    @Operation(summary = "查询Hub出库记录")
    @GetMapping("/outbound")
    public Result<List<HubOutboundRecord>> listOutbound(
            @RequestParam(required = false) Long hubId,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<HubOutboundRecord> wrapper = new LambdaQueryWrapper<>();
        if (hubId != null) wrapper.eq(HubOutboundRecord::getHubId, hubId);
        if (status != null) wrapper.eq(HubOutboundRecord::getStatus, status);
        wrapper.orderByDesc(HubOutboundRecord::getCreateTime);
        return Result.success(outboundMapper.selectList(wrapper));
    }

    @Operation(summary = "创建Hub出库记录")
    @PostMapping("/outbound")
    public Result<HubOutboundRecord> createOutbound(@RequestBody HubOutboundRecord record) {
        record.setStatus("PENDING");
        outboundMapper.insert(record);
        return Result.success(record);
    }

    @Operation(summary = "确认Hub出库")
    @PutMapping("/outbound/{id}/confirm")
    public Result<Boolean> confirmOutbound(@PathVariable Long id) {
        LambdaUpdateWrapper<HubOutboundRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HubOutboundRecord::getId, id)
                .set(HubOutboundRecord::getStatus, "DONE")
                .set(HubOutboundRecord::getOutboundTime, new Date());
        return Result.success(outboundMapper.update(null, wrapper) > 0);
    }
}
