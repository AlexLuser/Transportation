package com.fm.logistics.controller;

import com.fm.common.result.Result;
import com.fm.logistics.dto.HubDTO;
import com.fm.logistics.entity.LogisticsHub;
import com.fm.logistics.service.LogisticsHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流中转站管理 Controller
 *
 * GET    /api/logistics/hubs                    - 获取所有中转站列表
 * GET    /api/logistics/hubs/nearest            - 查询最近中转站
 * POST   /api/logistics/hubs                   - 新建中转站（admin）
 * PUT    /api/logistics/hubs/{id}               - 更新中转站（admin）
 * DELETE /api/logistics/hubs/{id}               - 删除（关闭）中转站（admin）
 */
@Tag(name = "物流中转站管理", description = "Hub-and-Spoke 中转站 CRUD 接口")
@RestController
@RequestMapping("/api/logistics/hubs")
public class LogisticsHubController {

    @Autowired
    private LogisticsHubService hubService;

    @Operation(summary = "获取所有中转站列表")
    @GetMapping
    public Result<List<LogisticsHub>> listHubs() {
        return Result.success(hubService.listHubs());
    }

    @Operation(summary = "查询距给定坐标最近的中转站")
    @GetMapping("/nearest")
    public Result<LogisticsHub> getNearestHub(@RequestParam double lat,
                                               @RequestParam double lng) {
        LogisticsHub hub = hubService.findNearestHub(lat, lng);
        if (hub == null) {
            return Result.error("暂无可用中转站");
        }
        return Result.success(hub);
    }

    @Operation(summary = "根据ID查询中转站")
    @GetMapping("/{id}")
    public Result<LogisticsHub> getHubById(@PathVariable Long id) {
        LogisticsHub hub = hubService.getHubById(id);
        if (hub == null) return Result.error("中转站不存在");
        return Result.success(hub);
    }

    @Operation(summary = "新建中转站（admin）")
    @PostMapping
    public Result<LogisticsHub> createHub(@RequestBody HubDTO hubDTO) {
        if (hubDTO.getName() == null || hubDTO.getAddress() == null
                || hubDTO.getLatitude() == null || hubDTO.getLongitude() == null) {
            return Result.error("名称、地址、坐标为必填项");
        }
        return Result.success(hubService.createHub(hubDTO));
    }

    @Operation(summary = "更新中转站信息（admin）")
    @PutMapping("/{id}")
    public Result<LogisticsHub> updateHub(@PathVariable Long id,
                                           @RequestBody HubDTO hubDTO) {
        hubDTO.setId(id);
        return Result.success(hubService.updateHub(hubDTO));
    }

    @Operation(summary = "删除（关闭）中转站（admin）")
    @DeleteMapping("/{id}")
    public Result<Void> deleteHub(@PathVariable Long id) {
        hubService.deleteHub(id);
        return Result.success(null);
    }
}
