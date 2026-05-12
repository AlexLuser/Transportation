package com.fm.logistics.dto;

import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCMF 图结构（构建残差网络前的内存表示）
 */
@Data
public class McmfGraphDTO {

    private List<NationalHub> hubs;
    private List<HubLink> links;

    /**
     * 各 Hub 的净供给/需求：
     * 正值 = 供给（发货城市），负值 = 需求（收货城市），0 = 纯中转
     * key = national_hub.id
     */
    private Map<Long, Integer> supplyDemand;
}
