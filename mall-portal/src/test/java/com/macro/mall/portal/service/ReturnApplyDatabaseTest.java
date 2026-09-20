package com.macro.mall.portal.service;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.domain.ReturnDeliveryParam;
import com.macro.mall.portal.service.impl.OmsPortalOrderReturnApplyServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in database regression: all application/log writes are rolled back. */
@EnabledIfEnvironmentVariable(named="MALL_RETURN_TEST_URL", matches=".+")
class ReturnApplyDatabaseTest {
 @Test void createReadAndReturnShippingWorkWithDatabaseSchema() throws Exception {
  var ds=new DriverManagerDataSource(System.getenv("MALL_RETURN_TEST_URL"),System.getenv("MALL_RETURN_TEST_USER"),System.getenv("MALL_RETURN_TEST_PASSWORD"));
  var config=new Configuration(new Environment("return-test",new JdbcTransactionFactory(),ds));
  for(String name:List.of("OmsOrderReturnApplyMapper","OmsOrderReturnApplyLogMapper")) {
   String resource="com/macro/mall/mapper/"+name+".xml";
   try(var stream=getClass().getClassLoader().getResourceAsStream(resource)) {
    new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
   }
  }
  var factory=new SqlSessionFactoryBuilder().build(config);
  Long createdId=null;
  try(var session=factory.openSession(false)) {
   try {
    var service=new OmsPortalOrderReturnApplyServiceImpl();
    var applications=session.getMapper(OmsOrderReturnApplyMapper.class);
    var logs=session.getMapper(OmsOrderReturnApplyLogMapper.class);
    var members=mock(UmsMemberService.class);var orders=mock(OmsOrderMapper.class);var items=mock(OmsOrderItemMapper.class);
    ReflectionTestUtils.setField(service,"returnApplyMapper",applications);ReflectionTestUtils.setField(service,"returnApplyLogMapper",logs);
    ReflectionTestUtils.setField(service,"memberService",members);ReflectionTestUtils.setField(service,"orderMapper",orders);ReflectionTestUtils.setField(service,"orderItemMapper",items);
    var member=new UmsMember();member.setId(-20260915L);member.setUsername("__return_schema_regression__");when(members.getCurrentMember()).thenReturn(member);
    var order=new OmsOrder();order.setId(-20260915L);order.setMemberId(member.getId());order.setStatus(3);order.setDeleteStatus(0);order.setOrderSn("schema-regression");when(orders.selectByPrimaryKey(order.getId())).thenReturn(order);
    var item=new OmsOrderItem();item.setProductId(56L);item.setProductQuantity(1);item.setProductName("售后事务回滚测试");item.setProductPrice(new BigDecimal("0.01"));item.setRealAmount(new BigDecimal("0.01"));when(items.selectByExample(any())).thenReturn(List.of(item));
    var param=new OmsOrderReturnApplyParam();param.setOrderId(order.getId());param.setProductId(item.getProductId());param.setProductCount(1);param.setReason("质量问题");param.setReturnName("事务测试");param.setReturnPhone("13800000000");
    assertEquals(1,service.create(param));
    var example=new OmsOrderReturnApplyExample();example.createCriteria().andOrderIdEqualTo(order.getId());
    var rows=applications.selectByExample(example);assertEquals(1,rows.size());createdId=rows.get(0).getId();
    var detail=service.detail(createdId);assertEquals("质量问题",detail.getReason());assertEquals(1,detail.getLogList().size());assertEquals("提交售后申请",detail.getLogList().get(0).getTitle());
    assertThrows(RuntimeException.class,()->service.create(param));
    var update=new OmsOrderReturnApply();update.setId(createdId);update.setStatus(1);applications.updateByPrimaryKeySelective(update);
    var shipping=new ReturnDeliveryParam();shipping.setId(createdId);shipping.setDeliveryCompany("测试快递");shipping.setDeliverySn("rollback-only");
    assertEquals(1,service.fillDelivery(shipping));
    var shipped=service.detail(createdId);assertEquals(4,shipped.getStatus());assertEquals("rollback-only",shipped.getReturnDeliverySn());assertEquals(2,shipped.getLogList().size());
   } finally {session.rollback(true);}
  }
  try(var session=factory.openSession()) {
   assertNull(session.getMapper(OmsOrderReturnApplyMapper.class).selectByPrimaryKey(createdId));
   assertTrue(session.getMapper(OmsOrderReturnApplyLogMapper.class).selectByApplyId(createdId).isEmpty());
  }
 }
}
