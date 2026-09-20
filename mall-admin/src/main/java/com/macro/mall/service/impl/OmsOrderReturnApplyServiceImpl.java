package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.dto.UmsMemberMessageParam;
import com.macro.mall.mapper.OmsOrderReturnApplyLogMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.model.OmsOrderReturnApplyLog;
import com.macro.mall.service.OmsOrderReturnApplyService;
import com.macro.mall.service.UmsMemberMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 订单退货管理Service实现类
 * 状态机：0待处理 -> 1退货中 -> 4待收货 -> 2已完成；0 -> 3已拒绝；5已取消（前台操作）
 * Created by macro on 2018/10/18.
 */
@Service
public class OmsOrderReturnApplyServiceImpl implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private OmsOrderReturnApplyLogMapper returnApplyLogMapper;
    @Autowired
    private UmsMemberMessageService memberMessageService;

    @Override
    public List<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        return returnApplyDao.getList(queryParam);
    }

    @Override
    public int delete(List<Long> ids) {
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andIdIn(ids).andStatusEqualTo(3);
        return returnApplyMapper.deleteByExample(example);
    }

    @Override
    @Transactional
    public int updateStatus(Long id, OmsUpdateStatusParam statusParam) {
        Integer status = statusParam.getStatus();
        OmsOrderReturnApply current = returnApplyMapper.selectByPrimaryKey(id);
        if (current == null) {
            return 0;
        }
        Integer fromStatus = current.getStatus();
        OmsOrderReturnApply returnApply = new OmsOrderReturnApply();
        String logTitle;
        String logNote;
        if (status.equals(1)) {
            //确认退货：仅 0待处理 -> 1退货中
            if (!Integer.valueOf(0).equals(fromStatus)) {
                return 0;
            }
            returnApply.setId(id);
            returnApply.setStatus(1);
            returnApply.setReturnAmount(statusParam.getReturnAmount());
            returnApply.setCompanyAddressId(statusParam.getCompanyAddressId());
            returnApply.setHandleTime(new Date());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
            logTitle = "商家已同意售后申请";
            logNote = "请按商家提供的地址寄回商品，并填写寄回物流单号";
        } else if (status.equals(2)) {
            //完成退货：1退货中 或 4待收货 -> 2已完成
            if (!Integer.valueOf(1).equals(fromStatus) && !Integer.valueOf(4).equals(fromStatus)) {
                return 0;
            }
            returnApply.setId(id);
            returnApply.setStatus(2);
            returnApply.setReceiveTime(new Date());
            returnApply.setReceiveMan(statusParam.getReceiveMan());
            returnApply.setReceiveNote(statusParam.getReceiveNote());
            logTitle = "商家已确认收货，退款完成";
            logNote = statusParam.getReceiveNote();
        } else if (status.equals(3)) {
            //拒绝退货：仅 0待处理 -> 3已拒绝
            if (!Integer.valueOf(0).equals(fromStatus)) {
                return 0;
            }
            returnApply.setId(id);
            returnApply.setStatus(3);
            returnApply.setHandleTime(new Date());
            returnApply.setHandleMan(statusParam.getHandleMan());
            returnApply.setHandleNote(statusParam.getHandleNote());
            logTitle = "商家已拒绝售后申请";
            logNote = statusParam.getHandleNote();
        } else {
            return 0;
        }
        int count = returnApplyMapper.updateByPrimaryKeySelective(returnApply);
        if (count > 0) {
            String operator = statusParam.getHandleMan();
            if (operator == null || operator.trim().isEmpty()) {
                operator = statusParam.getReceiveMan();
            }
            saveLog(id, status, logTitle, logNote, 1, operator);
            if (Integer.valueOf(1).equals(status)) {
                sendReturnApprovedMessage(current);
            } else if (Integer.valueOf(2).equals(status)) {
                sendRefundCompletedMessage(current);
            }
        }
        return count;
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        OmsOrderReturnApplyResult result = returnApplyDao.getDetail(id);
        if (result != null) {
            result.setLogList(returnApplyLogMapper.selectByApplyId(id));
        }
        return result;
    }

    private void saveLog(Long applyId, Integer status, String title, String note, Integer operatorType, String operatorName) {
        OmsOrderReturnApplyLog log = new OmsOrderReturnApplyLog();
        log.setApplyId(applyId);
        log.setStatus(status);
        log.setTitle(title);
        log.setNote(note);
        log.setOperatorType(operatorType);
        log.setOperatorName(operatorName);
        log.setCreateTime(new Date());
        returnApplyLogMapper.insert(log);
    }

    private void sendReturnApprovedMessage(OmsOrderReturnApply apply) {
        UmsMemberMessageParam message = new UmsMemberMessageParam();
        message.setTitle("售后申请已通过");
        message.setContent("您的订单 " + displayOrderSn(apply) + " 中“" + displayProductName(apply)
                + "”的退货申请已通过，请按售后详情中的地址寄回商品并填写物流信息。");
        memberMessageService.sendToOrderMember(apply.getOrderId(), message);
    }

    private void sendRefundCompletedMessage(OmsOrderReturnApply apply) {
        String amount = apply.getReturnAmount() == null ? "" : "，退款金额 ¥" + apply.getReturnAmount().toPlainString();
        UmsMemberMessageParam message = new UmsMemberMessageParam();
        message.setTitle("退款成功");
        message.setContent("您的订单 " + displayOrderSn(apply) + " 中“" + displayProductName(apply)
                + "”的售后已完成" + amount + "。请留意原支付账户的到账情况。");
        memberMessageService.sendToOrderMember(apply.getOrderId(), message);
    }

    private String displayOrderSn(OmsOrderReturnApply apply) {
        return apply.getOrderSn() == null || apply.getOrderSn().trim().isEmpty()
                ? String.valueOf(apply.getOrderId()) : apply.getOrderSn().trim();
    }

    private String displayProductName(OmsOrderReturnApply apply) {
        return apply.getProductName() == null || apply.getProductName().trim().isEmpty()
                ? "商品" : apply.getProductName().trim();
    }
}
