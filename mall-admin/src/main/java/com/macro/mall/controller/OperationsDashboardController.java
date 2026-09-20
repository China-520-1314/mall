package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/operationsDashboard")
public class OperationsDashboardController {
 @Autowired private JdbcTemplate jdbc;
 @GetMapping("/summary")
 @Transactional(readOnly=true)
 public CommonResult<?> summary(Principal principal, @RequestParam(defaultValue="7") int days) {
  if(principal==null || !"admin".equals(principal.getName())) Asserts.fail("仅系统管理员可以查看经营数据");
  if(days!=7 && days!=30) Asserts.fail("请选择近7天或近30天");
  String paid=" FROM oms_order WHERE delete_status=0 AND payment_time IS NOT NULL AND status IN (1,2,3) ";
  String recent=" AND create_time>=DATE_SUB(CURDATE(),INTERVAL ? DAY)";
  Map<String,Object> data=new LinkedHashMap<>();
  data.put("overview",jdbc.queryForMap("SELECT COUNT(*) paidOrders,COALESCE(SUM(pay_amount),0) paidAmount"+paid+recent,days-1));
  data.put("today",jdbc.queryForMap("SELECT COUNT(*) paidOrders,COALESCE(SUM(pay_amount),0) paidAmount"+paid+" AND payment_time>=CURDATE()"));
  data.put("members",jdbc.queryForObject("SELECT COUNT(*) FROM ums_member",Long.class));
  data.put("products",jdbc.queryForObject("SELECT COUNT(*) FROM pms_product WHERE delete_status=0 AND publish_status=1",Long.class));
  data.put("totalOrders",jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE delete_status=0",Long.class));
  data.put("comments",jdbc.queryForObject("SELECT COUNT(*) FROM pms_comment WHERE show_status=1",Long.class));
  data.put("shipped",jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE delete_status=0 AND status IN (2,3)",Long.class));
  data.put("pending",jdbc.queryForObject("SELECT COUNT(*) FROM oms_order WHERE delete_status=0 AND status=1",Long.class));
  data.put("returns",jdbc.queryForObject("SELECT COUNT(*) FROM oms_order_return_apply WHERE status IN (0,1,4)",Long.class));
  data.put("trend",jdbc.queryForList("SELECT DATE_FORMAT(DATE(create_time),'%Y-%m-%d') day,COUNT(*) orders,COALESCE(SUM(pay_amount),0) amount"+paid+recent+" GROUP BY DATE_FORMAT(DATE(create_time),'%Y-%m-%d') ORDER BY day",days-1));
  data.put("status",jdbc.queryForList("SELECT status,COUNT(*) value FROM oms_order WHERE delete_status=0"+recent+" GROUP BY status ORDER BY status",days-1));
  data.put("regions",jdbc.queryForList("SELECT COALESCE(NULLIF(receiver_province,''),'未填写') name,COUNT(*) value"+paid+recent+" GROUP BY receiver_province ORDER BY value DESC LIMIT 8",days-1));
  data.put("productsRank",jdbc.queryForList("SELECT i.product_id productId,COALESCE(p.name,MAX(i.product_name)) name,SUM(i.product_quantity) value FROM oms_order_item i JOIN oms_order o ON o.id=i.order_id LEFT JOIN pms_product p ON p.id=i.product_id WHERE o.delete_status=0 AND o.payment_time IS NOT NULL AND o.status IN (1,2,3) AND o.create_time>=DATE_SUB(CURDATE(),INTERVAL ? DAY) GROUP BY i.product_id,p.name ORDER BY value DESC LIMIT 6",days-1));
  data.put("payments",jdbc.queryForList("SELECT pay_type type,COUNT(*) value"+paid+recent+" GROUP BY pay_type",days-1));
  data.put("date",jdbc.queryForObject("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')",String.class));
  data.put("updatedAt",new Date());
  return CommonResult.success(data);
 }
}
