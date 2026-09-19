package com.aijiaoxue.mapper;

import com.aijiaoxue.entity.SysRolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色—权限覆写 Mapper。
 *
 * <p>表中有该角色的记录时视为已人工覆写，覆写优先于 PermissionCodes 默认矩阵。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /** 清空某角色的全部权限覆写 */
    @Delete("DELETE FROM sys_role_permission WHERE role_code = #{roleCode}")
    int deleteByRoleCode(@Param("roleCode") String roleCode);
}
