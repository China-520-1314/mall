package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsCompanyAddressMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyLogMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.model.*;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.domain.PortalReturnApplyDetail;
import com.macro.mall.portal.domain.ReturnDeliveryParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 前台订单售后管理Service实现类
 * 状态机：0待处理 -> 1退货中(商家同意) -> 4待收货(会员寄回) -> 2已完成(商家收货退款)
 *                     0 -> 3已拒绝(商家拒绝)；0 -> 5已取消(会员撤回)
 * Created by macro on 2018/10/17.
 */
@Service
public class OmsPortalOrderReturnApplyServiceImpl implements OmsPortalOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyMapper returnApplyMapper;
    @Autowired
    private OmsOrderReturnApplyLogMapper returnApplyLogMapper;
    @Autowired
    private OmsCompanyAddressMapper companyAddressMapper;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsOrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public int create(OmsOrderReturnApplyParam returnApply) {
        UmsMember member = memberService.getCurrentMember();
        if (returnApply == null || returnApply.getOrderId() == null || returnApply.getProductId() == null) {
            Asserts.fail("请选择订单和售后商品");
        }
        OmsOrder order = orderMapper.selectByPrimaryKey(returnApply.getOrderId());
        if (order == null || !member.getId().equals(order.getMemberId()) || Integer.valueOf(1).equals(order.getDeleteStatus())) {
            Asserts.fail("订单不存在");
        }
        if (order.getStatus() == null || !List.of(1, 2, 3).contains(order.getStatus())) {
            Asserts.fail("当前订单状态不允许申请售后");
        }
        OmsOrderItemExample itemExample = new OmsOrderItemExample();
        itemExample.createCriteria().andOrderIdEqualTo(order.getId()).andProductIdEqualTo(returnApply.getProductId());
        List<OmsOrderItem> items = orderItemMapper.selectByExample(itemExample);
        if (items.size() != 1) {
            Asserts.fail("无法确定售后订单项，请选择具体商品规格");
        }
        OmsOrderItem item = items.get(0);
        if (returnApply.getProductCount() == null || returnApply.getProductCount() < 1
                || returnApply.getProductCount() > item.getProductQuantity()) {
            Asserts.fail("退货数量超出购买数量");
        }
        // 防止同一商品存在处理中的售后单
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria()
                .andOrderIdEqualTo(returnApply.getOrderId())
                .andProductIdEqualTo(returnApply.getProductId())
                .andStatusIn(List.of(0, 1, 4));
        if (returnApplyMapper.countByExample(example) > 0) {
            Asserts.fail("该商品已有处理中的售后单，请勿重复申请");
        }
        OmsOrderReturnApply realApply = new OmsOrderReturnApply();
        BeanUtils.copyProperties(returnApply, realApply);
        // 商品信息和实付单价由订单项回填，拒绝客户端篡改退款基数。
        realApply.setOrderSn(order.getOrderSn());
        realApply.setProductName(item.getProductName());
        realApply.setProductPic(item.getProductPic());
        realApply.setProductBrand(item.getProductBrand());
        realApply.setProductAttr(item.getProductAttr());
        realApply.setProductPrice(item.getProductPrice());
        realApply.setProductRealPrice(item.getRealAmount());
        // 会员信息以服务端登录态为准，不信任前端传值
        realApply.setMemberUsername(member.getUsername());
        realApply.setCreateTime(new Date());
        realApply.setStatus(0);
        int count = returnApplyMapper.insert(realApply);
        // 落第一条进度日志
        saveLog(realApply.getId(), 0, "提交售后申请",
                StrUtil.format("申请原因：{}", returnApply.getReason()), 0, memberDisplayName(member));
        return count;
    }

    @Override
    public CommonPage<OmsOrderReturnApply> list(Integer pageNum, Integer pageSize) {
        UmsMember member = memberService.getCurrentMember();
        PageHelper.startPage(pageNum, pageSize);
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andMemberUsernameEqualTo(member.getUsername());
        example.setOrderByClause("create_time desc");
        List<OmsOrderReturnApply> list = returnApplyMapper.selectByExample(example);
        return CommonPage.restPage(list);
    }

    @Override
    public PortalReturnApplyDetail detail(Long id) {
        OmsOrderReturnApply apply = getOwnedApply(id);
        PortalReturnApplyDetail detail = new PortalReturnApplyDetail();
        BeanUtils.copyProperties(apply, detail);
        detail.setLogList(returnApplyLogMapper.selectByApplyId(id));
        // 商家同意后，展示退货收货地址
        if (apply.getCompanyAddressId() != null) {
            OmsCompanyAddress address = companyAddressMapper.selectByPrimaryKey(apply.getCompanyAddressId());
            if (address != null) {
                detail.setCompanyAddressName(address.getAddressName());
                detail.setCompanyReceiverName(address.getName());
                detail.setCompanyReceiverPhone(address.getPhone());
                detail.setCompanyDetailAddress(StrUtil.format("{}{}{}{}",
                        StrUtil.nullToEmpty(address.getProvince()),
                        StrUtil.nullToEmpty(address.getCity()),
                        StrUtil.nullToEmpty(address.getRegion()),
                        StrUtil.nullToEmpty(address.getDetailAddress())));
            }
        }
        return detail;
    }

    @Override
    public int fillDelivery(ReturnDeliveryParam param) {
        if (param.getId() == null || StrUtil.isBlank(param.getDeliveryCompany()) || StrUtil.isBlank(param.getDeliverySn())) {
            Asserts.fail("快递公司和单号不能为空");
        }
        OmsOrderReturnApply apply = getOwnedApply(param.getId());
        if (!Integer.valueOf(1).equals(apply.getStatus())) {
            Asserts.fail("当前状态不允许填写寄回物流");
        }
        OmsOrderReturnApply update = new OmsOrderReturnApply();
        update.setId(apply.getId());
        update.setStatus(4);
        update.setReturnDeliveryCompany(param.getDeliveryCompany());
        update.setReturnDeliverySn(param.getDeliverySn());
        update.setShipTime(new Date());
        int count = returnApplyMapper.updateByPrimaryKeySelective(update);
        saveLog(apply.getId(), 4, "已寄回商品，等待商家收货",
                StrUtil.format("{} 单号：{}", param.getDeliveryCompany(), param.getDeliverySn()),
                0, memberDisplayName(memberService.getCurrentMember()));
        return count;
    }

    @Override
    public int cancel(Long id) {
        OmsOrderReturnApply apply = getOwnedApply(id);
        if (!Integer.valueOf(0).equals(apply.getStatus())) {
            Asserts.fail("商家已处理，无法撤回申请");
        }
        OmsOrderReturnApply update = new OmsOrderReturnApply();
        update.setId(id);
        update.setStatus(5);
        int count = returnApplyMapper.updateByPrimaryKeySelective(update);
        saveLog(id, 5, "已撤销售后申请", null, 0, memberDisplayName(memberService.getCurrentMember()));
        return count;
    }

    /**
     * 按id查询售后单并校验归属权
     */
    private OmsOrderReturnApply getOwnedApply(Long id) {
        if (id == null) {
            Asserts.fail("售后单id不能为空");
        }
        OmsOrderReturnApply apply = returnApplyMapper.selectByPrimaryKey(id);
        if (apply == null) {
            Asserts.fail("售后单不存在");
        }
        UmsMember member = memberService.getCurrentMember();
        if (!member.getUsername().equals(apply.getMemberUsername())) {
            Asserts.fail("无权操作该售后单");
        }
        return apply;
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

    private String memberDisplayName(UmsMember member) {
        return StrUtil.blankToDefault(member.getNickname(), member.getUsername());
    }
}
