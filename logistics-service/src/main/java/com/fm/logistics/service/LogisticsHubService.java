package com.fm.logistics.service;

import com.fm.logistics.dto.HubDTO;
import com.fm.logistics.entity.LogisticsHub;

import java.util.List;

/**
 * 物流中转站服务接口
 */
public interface LogisticsHubService {

    /** 获取所有中转站列表 */
    List<LogisticsHub> listHubs();

    /**
     * 查询距离给定坐标最近的正常状态中转站
     *
     * @param lat 参考点纬度
     * @param lng 参考点经度
     * @return 最近的 Hub（null 表示无可用 Hub）
     */
    LogisticsHub findNearestHub(double lat, double lng);

    /** 根据ID查询 Hub */
    LogisticsHub getHubById(Long hubId);

    /** 新建中转站 */
    LogisticsHub createHub(HubDTO hubDTO);

    /** 更新中转站信息 */
    LogisticsHub updateHub(HubDTO hubDTO);

    /** 删除中转站（软删：status=2） */
    void deleteHub(Long hubId);
}
