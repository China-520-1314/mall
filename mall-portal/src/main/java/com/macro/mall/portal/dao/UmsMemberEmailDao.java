package com.macro.mall.portal.dao;

import org.apache.ibatis.annotations.Param;

/** 会员邮箱字段的自定义数据访问接口。 */
public interface UmsMemberEmailDao {
    int countByEmail(@Param("email") String email);

    Long selectMemberIdByEmail(@Param("email") String email);

    int updateEmail(@Param("memberId") Long memberId, @Param("email") String email);
}
