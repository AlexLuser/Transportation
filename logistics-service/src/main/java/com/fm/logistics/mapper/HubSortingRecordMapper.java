package com.fm.logistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fm.logistics.entity.HubSortingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HubSortingRecordMapper extends BaseMapper<HubSortingRecord> {

    /**
     * 批量将分拣记录标记为已分配批次（MCMF 规划完成后调用）
     */
    @Update("<script>" +
            "UPDATE hub_sorting_record " +
            "SET sort_result = 'ASSIGNED', assigned_batch_id = #{batchId}, sort_time = NOW() " +
            "WHERE sort_result = 'PENDING' AND order_id IN " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int batchAssignSorting(@Param("orderIds") List<Long> orderIds, @Param("batchId") Long batchId);
}
