package com.macro.mall.mapper;

import com.macro.mall.model.OmsOrderReturnApplyLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后进度日志Mapper
 * Created by trae on 2026/09/11.
 */
public interface OmsOrderReturnApplyLogMapper {

    int insert(OmsOrderReturnApplyLog record);

    List<OmsOrderReturnApplyLog> selectByApplyId(@Param("applyId") Long applyId);
}
