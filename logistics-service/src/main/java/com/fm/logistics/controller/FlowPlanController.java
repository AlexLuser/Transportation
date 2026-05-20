package com.fm.logistics.controller;

import com.fm.common.result.Result;
import com.fm.logistics.dto.FlowPlanDetailDTO;
import com.fm.logistics.service.FlowPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * MCMF 规划结果查询（只读）
 */
@RestController
@RequestMapping("/api/logistics/flow-plan")
@RequiredArgsConstructor
public class FlowPlanController {

    private final FlowPlanService flowPlanService;

    @GetMapping("/latest")
    public Result<FlowPlanDetailDTO> getLatest() {
        return Result.success(flowPlanService.getLatest());
    }

    @GetMapping("/{id}")
    public Result<FlowPlanDetailDTO> getById(@PathVariable Long id) {
        return Result.success(flowPlanService.getById(id));
    }
}
