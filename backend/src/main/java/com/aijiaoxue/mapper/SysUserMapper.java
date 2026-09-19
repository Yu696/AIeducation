package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 系统用户 Mapper（US-01 用户与权限管理）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按登录名查询账号 */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    /** 角色分布统计（管理员工作台） */
    @Select("""
            SELECT u.role_code AS group_key, r.role_name AS group_name, COUNT(1) AS cnt
            FROM sys_user u
            LEFT JOIN sys_role r ON r.role_code = u.role_code
            GROUP BY u.role_code, r.role_name
            ORDER BY cnt DESC
            """)
    List<Map<String, Object>> selectRoleDistribution();
}
