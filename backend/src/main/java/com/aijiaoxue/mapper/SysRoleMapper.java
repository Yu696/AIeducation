package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 角色清单 + 各角色账号数（权限矩阵页左侧下拉） */
    @Select("""
            SELECT r.role_code, r.role_name, r.description, r.data_scope,
                   (SELECT COUNT(1) FROM sys_user u WHERE u.role_code = r.role_code) AS user_count
            FROM sys_role r
            ORDER BY r.sort_no ASC
            """)
    List<Map<String, Object>> selectRoleWithUserCount();
}
