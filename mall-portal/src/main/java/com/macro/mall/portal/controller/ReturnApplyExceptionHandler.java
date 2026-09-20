package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.sql.SQLException;

/** 售后数据库异常保留服务端诊断信息，不向用户展示 SQL。 */
@RestControllerAdvice(assignableTypes = OmsPortalOrderReturnApplyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReturnApplyExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReturnApplyExceptionHandler.class);

    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public CommonResult<?> databaseFailure(Exception error) {
        LOGGER.error("售后数据操作失败", error);
        return CommonResult.failed("售后服务暂时不可用，请稍后重试");
    }
}
