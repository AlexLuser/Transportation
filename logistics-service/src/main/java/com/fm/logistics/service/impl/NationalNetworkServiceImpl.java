package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.logistics.dto.NationalHubDTO;
import com.fm.logistics.dto.NetworkTopologyDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;
import com.fm.logistics.mapper.HubLinkMapper;
import com.fm.logistics.mapper.InterCityBatchMapper;
import com.fm.logistics.mapper.NationalHubMapper;
import com.fm.logistics.mapper.OrderInfoMapper;
import com.fm.logistics.service.NationalNetworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NationalNetworkServiceImpl implements NationalNetworkService {

    private final NationalHubMapper nationalHubMapper;
    private final HubLinkMapper hubLinkMapper;
    private final InterCityBatchMapper interCityBatchMapper;
    private final OrderInfoMapper orderInfoMapper;

    @Override
    public List<NationalHub> listHubs(Integer level) {
        LambdaQueryWrapper<NationalHub> q = new LambdaQueryWrapper<>();
        if (level != null) q.eq(NationalHub::getHubLevel, level);
        q.ne(NationalHub::getStatus, 2);
        return nationalHubMapper.selectList(q);
    }

    @Override
    public NationalHub getHub(Long id) {
        return nationalHubMapper.selectById(id);
    }

    @Override
    public NationalHub saveHub(NationalHub hub) {
        nationalHubMapper.insert(hub);
        return hub;
    }

    @Override
    public NationalHub updateHub(Long id, NationalHub hub) {
        hub.setId(id);
        nationalHubMapper.updateById(hub);
        return nationalHubMapper.selectById(id);
    }

    @Override
    public List<HubLink> listLinks() {
        return hubLinkMapper.selectActiveLinks();
    }

    @Override
    public HubLink saveLink(HubLink link) {
        hubLinkMapper.insert(link);
        return link;
    }

    @Override
    public NetworkTopologyDTO getTopology() {
        List<NationalHub> hubs = nationalHubMapper.selectAllActive();
        List<HubLink> links = hubLinkMapper.selectActiveLinks();

        // 一次性批量查询各 Hub 的实时/今日负载，避免 N+1
        // 当前负载：CREATED 批次等待发车的货量（实时，随发车动态变化）
        Map<Long, Integer> pendingMap = toHubIntMap(interCityBatchMapper.selectCurrentPendingByHub());
        // 今日预计最大负载：order_info 中所有活跃跨城订单数（不依赖批次，从有订单起即可展示）
        Map<Long, Integer> todayMap   = toHubIntMap(orderInfoMapper.selectActiveCrossCityCountByOriginHub());

        List<NationalHubDTO> hubDTOs = hubs.stream().map(h -> {
            NationalHubDTO dto = new NationalHubDTO();
            dto.setId(h.getId());
            dto.setName(h.getName());
            dto.setHubLevel(h.getHubLevel());
            dto.setProvince(h.getProvince());
            dto.setCity(h.getCity());
            dto.setLatitude(h.getLatitude());
            dto.setLongitude(h.getLongitude());
            dto.setMaxCapacity(h.getMaxCapacity());
            dto.setCurrentLoad(h.getCurrentLoad());
            dto.setStatus(h.getStatus());

            int cap = h.getMaxCapacity() != null && h.getMaxCapacity() > 0 ? h.getMaxCapacity() : Integer.MAX_VALUE;

            // 静态负载率（来自表字段）
            double loadRate = h.getMaxCapacity() != null && h.getMaxCapacity() > 0
                    ? (double) (h.getCurrentLoad() != null ? h.getCurrentLoad() : 0) / h.getMaxCapacity() : 0;
            dto.setLoadRate(Math.min(loadRate, 1.0));

            // 实时负载：CREATED 批次等待发车的货量
            int pending = pendingMap.getOrDefault(h.getId(), 0);
            dto.setCurrentPendingLoad(pending);
            dto.setCurrentPendingLoadRate(Math.min((double) pending / cap, 1.0));

            // 今日最大负载：今日所有激活批次流过此 Hub 的货量
            int todayTotal = todayMap.getOrDefault(h.getId(), 0);
            dto.setTodayMaxLoad(todayTotal);
            dto.setTodayMaxLoadRate(Math.min((double) todayTotal / cap, 1.0));

            return dto;
        }).collect(Collectors.toList());

        NetworkTopologyDTO topo = new NetworkTopologyDTO();
        topo.setHubs(hubDTOs);
        topo.setLinks(links);
        return topo;
    }

    /** 将 [{hubId, total}] 聚合结果转为 Map&lt;hubId, total&gt; */
    private Map<Long, Integer> toHubIntMap(List<Map<String, Object>> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("hubId")).longValue(),
                r -> ((Number) r.get("total")).intValue(),
                Integer::sum
        ));
    }
}
