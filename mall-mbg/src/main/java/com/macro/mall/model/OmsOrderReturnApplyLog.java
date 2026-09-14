package com.macro.mall.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Date;

/**
 * 售后进度日志
 * Created by trae on 2026/09/11.
 */
public class OmsOrderReturnApplyLog implements Serializable {
    private Long id;

    @Schema(title = "售后单id")
    private Long applyId;

    @Schema(title = "节点状态：0->待处理；1->退货中；2->已完成；3->已拒绝；4->待收货；5->已取消")
    private Integer status;

    @Schema(title = "节点标题")
    private String title;

    @Schema(title = "节点备注")
    private String note;

    @Schema(title = "操作者类型：0->会员；1->商家；2->系统")
    private Integer operatorType;

    @Schema(title = "操作者名称")
    private String operatorName;

    @Schema(title = "创建时间")
    private Date createTime;

    private static final long serialVersionUID = 1L;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType) {
        this.operatorType = operatorType;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
