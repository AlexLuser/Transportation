package com.fm.logistics.dto;

import com.fm.logistics.entity.HubLink;
import lombok.Data;

import java.util.List;

/**
 * 全国网络拓扑（节点 + 边，供前端地图渲染）
 */
@Data
public class NetworkTopologyDTO {

    private List<NationalHubDTO> hubs;
    private List<HubLink> links;
}
