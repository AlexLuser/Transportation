package com.fm.logistics.controller;

import com.fm.common.result.Result;
import com.fm.logistics.dto.NetworkTopologyDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;
import com.fm.logistics.service.NationalNetworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全国 Hub 网络管理（MCMF 网络节点/边的 CRUD）
 */
@RestController
@RequestMapping("/api/logistics/national-network")
@RequiredArgsConstructor
public class NationalNetworkController {

    private final NationalNetworkService nationalNetworkService;

    @GetMapping("/hubs")
    public Result<List<NationalHub>> listHubs(
            @RequestParam(required = false) Integer level) {
        return Result.success(nationalNetworkService.listHubs(level));
    }

    @GetMapping("/hubs/{id}")
    public Result<NationalHub> getHub(@PathVariable Long id) {
        return Result.success(nationalNetworkService.getHub(id));
    }

    @PostMapping("/hubs")
    public Result<NationalHub> saveHub(@RequestBody NationalHub hub) {
        return Result.success(nationalNetworkService.saveHub(hub));
    }

    @PutMapping("/hubs/{id}")
    public Result<NationalHub> updateHub(@PathVariable Long id,
                                          @RequestBody NationalHub hub) {
        return Result.success(nationalNetworkService.updateHub(id, hub));
    }

    @GetMapping("/links")
    public Result<List<HubLink>> listLinks() {
        return Result.success(nationalNetworkService.listLinks());
    }

    @PostMapping("/links")
    public Result<HubLink> saveLink(@RequestBody HubLink link) {
        return Result.success(nationalNetworkService.saveLink(link));
    }

    @GetMapping("/topology")
    public Result<NetworkTopologyDTO> getTopology() {
        return Result.success(nationalNetworkService.getTopology());
    }
}
